package com.iscs.ratingbunny.domains

import cats.data.EitherT
import cats.effect.*
import cats.implicits.*
import com.iscs.mail.EmailService
import com.iscs.ratingbunny.util.{DeterministicHash, PasswordHasher}
import com.typesafe.scalalogging.Logger
import mongo4cats.circe.*
import mongo4cats.collection.MongoCollection
import mongo4cats.models.collection.IndexOptions
import mongo4cats.operations.Index
import org.mongodb.scala.model.Updates as JUpdates

import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.concurrent.TimeUnit
import io.circe.{Codec, Decoder, Encoder}

enum PasswordResetError:
  case InvalidOrExpired
  case WeakPassword

trait PasswordResetService[F[_]]:
  def requestReset(req: PasswordResetRequest, requestIp: Option[String] = None): F[Unit]
  def confirmReset(req: PasswordResetConfirmRequest): F[Either[PasswordResetError, Unit]]

final case class PasswordResetTokenDoc(
    userId: String,
    tokenHash: String,
    createdAt: Instant,
    expiresAt: Instant,
    usedAt: Option[Instant] = None,
    requestIp: Option[String] = None
)

class PasswordResetServiceImpl[F[_]: Async](
    usersCol: MongoCollection[F, UserDoc],
    tokenCol: MongoCollection[F, PasswordResetTokenDoc],
    hasher: PasswordHasher[F],
    emailService: EmailService[F],
    tokenIssuer: TokenIssuer[F],
    resetHost: String,
    ttlMinutes: Long = 30L
) extends PasswordResetService[F] with QuerySetup:
  private val L              = Logger[this.type]
  private val random        = SecureRandom()
  private val minPasswordLen = 8

  private def ensureIndexes: F[Unit] =
    val idxName     = "tokenHash_idx"
    val ttlIdxName  = "tokenExpires_idx"
    val ttlIndex    = Index.ascending("expiresAt")
    val ttlOptions  = IndexOptions(background = true).name(ttlIdxName).expireAfter(0L, TimeUnit.SECONDS)
    val tokenIndex  = Index.ascending("tokenHash")
    val tokenOption = IndexOptions(background = true, unique = true).name(idxName)
    for
      idxDocs <- tokenCol.listIndexes
      existing = idxDocs.flatMap(_.getString("name").toList).toSet
      _ <- List(
        Option.when(!existing.contains(idxName))(tokenCol.createIndex(tokenIndex, tokenOption).void),
        Option.when(!existing.contains(ttlIdxName))(tokenCol.createIndex(ttlIndex, ttlOptions).void)
      ).flatten.sequence_
    yield ()

  private def generateToken(): F[(String, String)] =
    Async[F].delay {
      val bytes = new Array[Byte](32)
      random.nextBytes(bytes)
      val token     = Base64.getUrlEncoder.withoutPadding().encodeToString(bytes)
      val tokenHash = DeterministicHash.sha256(token)
      (token, tokenHash)
    }

  private def normalizeIdentifier(raw: String): String = raw.trim

  private def validatePw(pw: String): Either[PasswordResetError, Unit] =
    val ok =
      pw.length >= minPasswordLen &&
        pw.exists(_.isLower) &&
        pw.exists(_.isUpper) &&
        pw.exists(_.isDigit)
    if ok then Right(()) else Left(PasswordResetError.WeakPassword)

  override def requestReset(req: PasswordResetRequest, requestIp: Option[String] = None): F[Unit] =
    val identifier = normalizeIdentifier(req.identifier)
    val lookupUser: F[Option[UserDoc]] =
      if identifier.contains("@") then usersCol.find(feq("email_norm", identifier.toLowerCase)).first
      else usersCol.find(feq("userid", identifier)).first

    ensureIndexes *> lookupUser.flatMap:
      case None => Async[F].unit
      case Some(user) if !user.emailVerified => Async[F].unit
      case Some(user) =>
        val program = for
          now        <- EitherT.liftF[F, Nothing, Instant](Async[F].delay(Instant.now()))
          (token, h) <- EitherT.liftF[F, Nothing, (String, String)](generateToken())
          expires    <- EitherT.liftF[F, Nothing, Instant](Async[F].delay(now.plus(ttlMinutes, ChronoUnit.MINUTES)))
          doc = PasswordResetTokenDoc(user.userid, h, now, expires, None, requestIp)
          _ <- EitherT.liftF[F, Nothing, Unit](tokenCol.deleteMany(feq("userId", user.userid)).void)
          _ <- EitherT.liftF[F, Nothing, Unit](tokenCol.insertOne(doc).void)
          link = s"${resetHost.stripSuffix("/")}" + s"/reset-password?token=$token"
          _ <- EitherT.liftF[F, Nothing, Unit](emailService.sendEmail(user.email, "Reset your password", link, link).void)
        yield ()

        program.value.void.handleErrorWith: e =>
          Async[F].delay(L.error("failed to issue password reset", e))

  override def confirmReset(req: PasswordResetConfirmRequest): F[Either[PasswordResetError, Unit]] =
    val hashed = DeterministicHash.sha256(req.token)
    val program = for
      now   <- EitherT.liftF(Async[F].delay(Instant.now()))
      token <- EitherT.fromOptionF(tokenCol.find(feq("tokenHash", hashed)).first, PasswordResetError.InvalidOrExpired)
      _ <- EitherT.fromOption(
        Option.when(token.usedAt.isEmpty && token.expiresAt.isAfter(now))(()),
        PasswordResetError.InvalidOrExpired
      )
      _ <- EitherT.fromEither[F](validatePw(req.newPassword))
      user <- EitherT.fromOptionF(usersCol.find(feq("userid", token.userId)).first, PasswordResetError.InvalidOrExpired)
      newHash <- EitherT.liftF(hasher.hash(req.newPassword))
      _ <- EitherT.liftF(usersCol.updateOne(feq("userid", user.userid), JUpdates.set("passwordHash", newHash)).void)
      usedAt <- EitherT.liftF(Async[F].delay(Instant.now()))
      _      <- EitherT.liftF(tokenCol.updateOne(feq("tokenHash", hashed), JUpdates.set("usedAt", usedAt)).void)
      _      <- EitherT.liftF(tokenIssuer.revokeUser(user.userid))
    yield ()

    program.value

object PasswordResetServiceImpl:
  def make[F[_]: Async](
      usersCol: MongoCollection[F, UserDoc],
      tokenCol: MongoCollection[F, PasswordResetTokenDoc],
      hasher: PasswordHasher[F],
      emailService: EmailService[F],
      tokenIssuer: TokenIssuer[F],
      resetHost: String,
      ttlMinutes: Long = 30L
  ): PasswordResetService[F] =
    new PasswordResetServiceImpl[F](usersCol, tokenCol, hasher, emailService, tokenIssuer, resetHost, ttlMinutes)

object PasswordResetTokenDoc:
  import io.circe.generic.semiauto.*
  given instantCodec: Codec[Instant] = Codec.from(
    Decoder.decodeLong.map(Instant.ofEpochMilli),
    Encoder.encodeLong.contramap[Instant](_.toEpochMilli)
  )
  given Codec[PasswordResetTokenDoc] = deriveCodec[PasswordResetTokenDoc]
