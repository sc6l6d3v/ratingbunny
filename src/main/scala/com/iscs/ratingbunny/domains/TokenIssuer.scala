package com.iscs.ratingbunny.domains

import cats.effect.{Async, Clock, Sync}
import cats.implicits.*
import com.iscs.ratingbunny.domains.{TokenPair, UserDoc}
import dev.profunktor.redis4cats.RedisCommands
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

object TokenIssuer:
  def apply[F[_]](using ev: TokenIssuer[F]): TokenIssuer[F] = ev

given Conversion[FiniteDuration, Instant] with
  def apply(fd: FiniteDuration): Instant = Instant.ofEpochMilli(fd.toMillis)

class TokenIssuerImpl[F[_]: Async](
    redis: RedisCommands[F, String, String],
    userRepo: UserRepo[F],
    key: String,
    accessTtl: FiniteDuration = 15.minutes,
    refreshTtl: FiniteDuration = 30.days
) extends TokenIssuer[F]:

  private object Hash:
    private val md                = java.security.MessageDigest.getInstance("SHA-256")
    def sha256(s: String): String = md.digest(s.getBytes("UTF-8")).map("%02x" format _).mkString

  override def issue(user: UserDoc): F[TokenPair] =
    issueGuest(user.userid)

  override def issueGuest(uid: String): F[TokenPair] =
    for
      nowDuration <- Clock[F].realTime
      jti         <- Sync[F].delay(UUID.randomUUID().toString)
      jwt = JwtCirce.encode(
        JwtClaim(
          subject = Some(uid),
          issuedAt = Some(nowDuration.length),
          expiration = Some((nowDuration + accessTtl).length),
          jwtId = Some(jti)
        ),
        key,
        HS256
      )
      rawRefresh <- Sync[F].delay(UUID.randomUUID().toString)
      _          <- redis.setEx(s"refresh:${Hash.sha256(rawRefresh)}", uid, refreshTtl)
    yield TokenPair(jwt, rawRefresh)

  override def rotate(raw: String): F[Option[TokenPair]] =
    val key = s"refresh:${Hash.sha256(raw)}"
    for
      uidOpt <- redis.getDel(key)
      res <- uidOpt.traverse { uid =>
        if uid.startsWith("guest-") then
          issueGuest(uid).map(Option(_))
        else
          userRepo.findByUserId(uid).flatMap {
            case Some(user) => issue(user).map(Option(_))
            case None       => Option.empty[TokenPair].pure[F]
          }
      }
    yield res.flatten

  override def revoke(raw: String): F[Unit] =
    redis.del(s"refresh:${Hash.sha256(raw)}").void
