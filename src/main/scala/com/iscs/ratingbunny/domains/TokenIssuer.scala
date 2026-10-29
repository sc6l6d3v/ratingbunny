package com.iscs.ratingbunny.domains

import cats.effect.{Async, Clock, Sync}
import cats.implicits.*
import com.iscs.ratingbunny.domains.{TokenPair, UserDoc}
import dev.profunktor.redis4cats.RedisCommands
import dev.profunktor.redis4cats.effects.ScriptOutputType
import pdi.jwt
import pdi.jwt.JwtAlgorithm.HS256
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration.*
import scala.language.implicitConversions

trait TokenIssuer[F[_]]:
  def issue(user: UserDoc): F[TokenPair] // access + refresh
  def issueGuest(uid: String): F[TokenPair]
  def rotate(refresh: String): F[Option[TokenPair]]
  def revoke(refresh: String): F[Unit]
  def revokeUser(uid: String): F[Unit]

object TokenIssuer:
  def apply[F[_]](using ev: TokenIssuer[F]): TokenIssuer[F] = ev

class TokenIssuerImpl[F[_]: Async](
    redis: RedisCommands[F, String, String],
    userRepo: UserRepo[F],
    key: String,
    accessTtl: FiniteDuration = 15.minutes,
    refreshTtl: FiniteDuration = 30.days
) extends TokenIssuer[F]:

  extension (fd: FiniteDuration) def toInstant: Instant = Instant.ofEpochMilli(fd.toMillis)

  private object Hash:
    // Constructed per call: MessageDigest is stateful and not thread-safe, so a shared
    // instance can interleave between fibers and yield a wrong hash.
    def sha256(s: String): String =
      java.security.MessageDigest
        .getInstance("SHA-256")
        .digest(s.getBytes("UTF-8"))
        .map("%02x" format _)
        .mkString

  private def refreshKey(raw: String): String = s"refresh:${Hash.sha256(raw)}"

  // Sorted set of a user's live refresh keys, scored by expiry epoch second, so revokeUser
  // reads one key instead of scanning every session in Redis.
  private def userIndexKey(uid: String): String = s"refresh-user:$uid"

  // Lua script for atomic getDel operation
  private val getDelScript =
    """
    local value = redis.call('GET', KEYS[1])
    if value then
        redis.call('DEL', KEYS[1])
    end
    return value
    """

  // Stores the token and indexes it under its user in one step, so no token exists that
  // revokeUser cannot find. Also prunes index entries whose tokens have already expired.
  // KEYS: refresh key, user index key. ARGV: uid, ttl seconds, now epoch seconds.
  private val issueScript =
    """
    local ttl = tonumber(ARGV[2])
    local now = tonumber(ARGV[3])
    redis.call('SET', KEYS[1], ARGV[1], 'EX', ttl)
    redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', now)
    redis.call('ZADD', KEYS[2], now + ttl, KEYS[1])
    redis.call('EXPIRE', KEYS[2], ttl)
    return 1
    """

  private def getDel(key: String): F[Option[String]] =
    redis
      .eval(getDelScript, ScriptOutputType.Value, List(key))
      .map(Option(_))
      .recover:
        case _: NoSuchElementException => None
        case _                         => None

  /** Removes one refresh token and its index entry; yields the uid it belonged to. */
  private def consume(key: String): F[Option[String]] =
    getDel(key).flatTap(_.traverse_(uid => redis.zRem(userIndexKey(uid), key)))

  override def issue(user: UserDoc): F[TokenPair] =
    issueGuest(user.userid)

  override def issueGuest(uid: String): F[TokenPair] =
    for
      nowDuration <- Clock[F].realTime
      jti         <- Sync[F].delay(UUID.randomUUID().toString)
      jwt = JwtCirce.encode(
        JwtClaim(
          subject = Some(uid),
          issuedAt = Some(nowDuration.toInstant.getEpochSecond),
          expiration = Some((nowDuration + accessTtl).toInstant.getEpochSecond),
          jwtId = Some(jti)
        ),
        key,
        HS256
      )
      rawRefresh <- Sync[F].delay(UUID.randomUUID().toString)
      _ <- redis.eval(
        issueScript,
        ScriptOutputType.Integer,
        List(refreshKey(rawRefresh), userIndexKey(uid)),
        List(uid, refreshTtl.toSeconds.toString, nowDuration.toSeconds.toString)
      )
    yield TokenPair(jwt, rawRefresh)

  override def rotate(raw: String): F[Option[TokenPair]] =
    for
      uidOpt <- consume(refreshKey(raw))
      res <- uidOpt.traverse: uid =>
        if uid.startsWith("guest-") then issueGuest(uid).map(Option(_))
        else
          userRepo
            .findByUserId(uid)
            .flatMap:
              case Some(user) => issue(user).map(Option(_))
              case None       => Option.empty[TokenPair].pure[F]
    yield res.flatten

  override def revoke(raw: String): F[Unit] =
    consume(refreshKey(raw)).void

  override def revokeUser(uid: String): F[Unit] =
    val index = userIndexKey(uid)
    redis
      .zRange(index, 0, -1)
      .flatMap:
        case Nil           => Async[F].unit
        case first :: rest => redis.del(first, rest*) *> redis.zRem(index, first, rest*).void
