package com.iscs.ratingbunny.domains

import cats.data.EitherT
import cats.effect.*
import cats.effect.kernel.Ref
import cats.implicits.*
import com.iscs.ratingbunny.config.{TrialConfig, TrialWindow}
import com.iscs.ratingbunny.messaging.EmailJob
import com.iscs.ratingbunny.util.{DeterministicHash, PasswordHasher}
import com.mongodb.ErrorCategory.DUPLICATE_KEY
import com.mongodb.MongoWriteException
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
  case BillingRequired
  case BillingFailed(message: String)

final class AuthCheckImpl[F[_]: Async](
    usersCol: MongoCollection[F, UserDoc],
    userProfileCol: MongoCollection[F, UserProfileDoc],
    billingCol: MongoCollection[F, BillingInfo],
    hasher: PasswordHasher[F],
    publishEmailJob: EmailJob => F[Unit],
    billingWorkflow: BillingWorkflow[F],
    trialConfig: TrialConfig
) extends AuthCheck[F] with QuerySetup:
  private val docFail    = 121
  private val MINPWDLEN  = 8
  private val VERIFYHOST = sys.env.getOrElse("VERIFYHOST", "https://example.com/")

  private def persistBilling(
      user: UserDoc,
      billing: Option[SignupBilling],
      trialWindow: TrialWindow
  ): EitherT[F, SignupError, Unit] =
    user.plan match
      case Plan.Free => EitherT.rightT[F, SignupError](())
      case Plan.ProMonthly | Plan.ProYearly =>
        billing match
          case Some(details) =>
            for
              doc <- billingWorkflow.createBilling(user, details, trialWindow)
              enriched = doc.copy(trialEndsAt = trialWindow.endsAt)
              _ <- EitherT.liftF(billingCol.insertOne(enriched).void)
            yield ()
          case None => EitherT.leftT[F, Unit](SignupError.BillingRequired)

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
    Ref
      .of[F, Option[String]](None)
      .flatMap: userIdRef =>
        val program = for
          _ <- EitherT.fromEither[F](validatePw(req.password))

          email_norm = req.email.trim.toLowerCase

          _ <- EitherT:
            usersCol
              .count(feq("email_norm", email_norm))
              .map:
                case 0 => Right(())
                case _ => Left(SignupError.EmailExists)
          uid         <- EitherT.liftF(genUserId(req.email))
          hash        <- EitherT.liftF(hasher.hash(req.password))
          token       <- EitherT.liftF(Sync[F].delay(UUID.randomUUID().toString))
          tokenHash   <- EitherT.liftF(Sync[F].delay(DeterministicHash.sha256(token)))
          expiresAt   <- EitherT.liftF(Sync[F].delay(Instant.now.plus(1, ChronoUnit.DAYS)))
          trialWindow <- EitherT.liftF(Sync[F].delay(trialConfig.windowFor(req.plan, Instant.now())))
          user = UserDoc(
            email = req.email,
            email_norm = email_norm,
            passwordHash = hash,
            userid = uid,
            plan = req.plan,
            status = trialWindow.statusDuringTrial,
            displayName = req.displayName,
            verificationTokenHash = Some(tokenHash),
            verificationExpires = Some(expiresAt),
            trialEndsAt = trialWindow.endsAt
          )
          _ <- EitherT.liftF(usersCol.insertOne(user) *> userIdRef.set(Some(uid)))
          _ <- EitherT.liftF(userProfileCol.insertOne(UserProfileDoc(uid)).void)
          _ <- persistBilling(user, req.billing, trialWindow)
          link = s"${VERIFYHOST}api/v3/auth/verify?token=$token"
          _ <- EitherT.liftF(Sync[F].delay(L.debug(s"saved $token with $tokenHash")))
          _ <- EitherT(
            publishEmailJob(
              EmailJob(
                EmailJob.KindVerifySignup,
                user._id.toHexString,
                correlationId = Some(uid)
              )
            ).attempt.map:
              case Right(_) => Right(())
              case Left(_)  => Left(SignupError.BadEmail)
          )
        yield SignupOK(uid)

        program.value.attempt.flatMap:
          case Left(e) =>
            cleanupSignup(userIdRef) *> handleSignupException(e)
          case Right(Left(err)) =>
            cleanupSignup(userIdRef) *> (Left(err): Either[SignupError, SignupOK]).pure[F]
          case Right(Right(ok)) =>
            Right(ok).pure[F]

  private def cleanupSignup(userIdRef: Ref[F, Option[String]]): F[Unit] =
    userIdRef.get.flatMap:
      case Some(uid) =>
        val deleteOps: List[F[Unit]] = List(
          billingCol.deleteOne(feq("userId", uid)).void,
          userProfileCol.deleteOne(feq("userid", uid)).void,
          usersCol.deleteOne(feq("userid", uid)).void
        )
        deleteOps.sequence_.handleErrorWith: e =>
          L.error(s"Failed to cleanup signup artifacts for $uid: ${e.getMessage}", e)
          Async[F].unit
      case None => Async[F].unit

  private def handleSignupException(e: Throwable): F[Either[SignupError, SignupOK]] =
    (e match
      case mw: MongoWriteException if mw.getError.getCategory == DUPLICATE_KEY =>
        val msg = Option(mw.getError.getMessage).getOrElse("")
        if msg.contains("email_norm") then Left(SignupError.EmailExists)
        else Left(SignupError.UserIdExists)
      case mw: MongoWriteException
          if mw.getError.getCode == docFail ||
            Option(mw.getError.getMessage).exists(_.startsWith("Document failed validation")) =>
        Left(SignupError.InvalidEmail)
      case _: MessagingException =>
        Left(SignupError.BadEmail)
      case other =>
        L.error(s"Error during signup: ${other.getMessage}", other)
        Left(SignupError.BadPassword)
    ).pure[F]
