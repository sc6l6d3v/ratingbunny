package com.iscs.ratingbunny.domains

import cats.data.EitherT
import cats.effect.*
import cats.implicits.*
import com.iscs.mail.EmailService
import com.iscs.ratingbunny.util.{DeterministicHash, PasswordHasher}
import com.mongodb.ErrorCategory.DUPLICATE_KEY
import com.mongodb.{ErrorCategory, MongoWriteException}
import jakarta.mail.MessagingException
import mongo4cats.circe.*
import mongo4cats.collection.MongoCollection

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

import scala.language.implicitConversions

trait AuthCheck[F[_]]:
  def signup(req: SignupRequest): F[Either[SignupError, SignupOK]] // returns userid

enum SignupError:
  case EmailExists
  case InvalidEmail
  case UserIdExists
  case BadPassword
  case BadEmail
  case BadBirthDate
  case BadGender
  case BadCountry
  case BadLanguage
  case BadTimezone

final class AuthCheckImpl[F[_]: Async](
    usersCol: MongoCollection[F, UserDoc],
    userProfileCol: MongoCollection[F, UserProfileDoc],
    hasher: PasswordHasher[F],
    emailService: EmailService[F]
) extends AuthCheck[F] with QuerySetup:
  private val docFail    = 121
  private val MINPWDLEN  = 8
  private val VERIFYHOST = sys.env.getOrElse("VERIFYHOST", "https://example.com/")

  private def validatePw(pw: String): Either[SignupError, Unit] =
    val ok =
      pw.length >= MINPWDLEN &&
        pw.exists(_.isLower) &&
        pw.exists(_.isUpper) &&
        pw.exists(_.isDigit)
    if ok then Right(()) else Left(SignupError.BadPassword)

  private def genUserId(base: String): F[String] =
    val seed = base.takeWhile(_ != '@')
    def loop(i: Int): F[String] =
      val candidate = if (i == 0) seed else s"$seed$i"
      usersCol
        .count(feq("userid", candidate))
        .flatMap:
          case 0 => candidate.pure[F]
          case _ => loop(i + 1)
    loop(0)

  override def signup(req: SignupRequest): F[Either[SignupError, SignupOK]] =
    (for
      // EitherT gives you an Either along the way
      _ <- EitherT.fromEither[F](validatePw(req.password))

      email_norm = req.email.trim.toLowerCase

      _ <- EitherT:
        usersCol
          .count(feq("email_norm", email_norm))
          .map:
            case 0 => Right(())
            case _ => Left(SignupError.EmailExists)
      uid        <- EitherT.liftF(genUserId(req.email))
      hash       <- EitherT.liftF(hasher.hash(req.password))
      token      <- EitherT.liftF(Sync[F].delay(UUID.randomUUID().toString))
      tokenHash <- EitherT.liftF(Sync[F].delay(DeterministicHash.sha256(token)))
      expiresAt  <- EitherT.liftF(Sync[F].delay(Instant.now.plus(1, ChronoUnit.DAYS)))
      user = UserDoc(
        email = req.email,
        email_norm = email_norm,
        passwordHash = hash,
        userid = uid,
        plan = req.plan,
        status = SubscriptionStatus.Active,
        displayName = req.displayName,
        verificationTokenHash = Some(tokenHash),
        verificationExpires = Some(expiresAt)
      )
      _ <- EitherT.liftF(usersCol.insertOne(user))
      _ <- EitherT.liftF(userProfileCol.insertOne(UserProfileDoc(uid)))
      link = s"${VERIFYHOST}api/v3/auth/verify?token=$token"
      _ <- EitherT.liftF(Sync[F].delay(L.debug(s"saved $token with $tokenHash")))
      _ <- EitherT.liftF(emailService.sendEmail(req.email, "Verify your email", link, link).void)
    yield SignupOK(uid)).value
      .handleError:
        case mw: MongoWriteException if mw.getError.getCategory == DUPLICATE_KEY =>
          val msg = Option(mw.getError.getMessage).getOrElse("")
          if (msg.contains("email_norm")) Left(SignupError.EmailExists)
          else Left(SignupError.UserIdExists)
        case mw: MongoWriteException
            if mw.getError.getCode == docFail ||
              mw.getError.getMessage.startsWith("Document failed validation") =>
          Left(SignupError.InvalidEmail)
        case _: MessagingException =>
          Left(SignupError.BadEmail)
        case e =>
          L.error(s"Error during signup: ${e.getMessage}", e)
          Left(SignupError.BadPassword) // or a generic failure
