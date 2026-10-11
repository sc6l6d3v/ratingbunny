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
import org.mongodb.scala.model.Filters as JFilters

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.{Locale, UUID}

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
  private val docFail        = 121
  private val MINPWDLEN      = 8
  private val MAXUSERIDTRIES = 10
  private val MAXUSERIDBASE  = 32
  private val MINUSERID      = 3
  private val GUESTPREFIX    = "guest-"
  private val userIdChars    = (('a' to 'z') ++ ('0' to '9') ++ "._-").toSet
  private val dupKeyField    = """dup key: \{ (\w+):""".r
  private val VERIFYHOST     = sys.env.getOrElse("VERIFYHOST", "https://example.com/")

  /** Deletes for the documents this signup inserted, newest first, so a failed signup removes exactly its own writes. */
  private type Undo = Ref[F, List[F[Unit]]]

  private def insertTracked[A](col: MongoCollection[F, A], doc: A, undo: Undo): F[Unit] =
    col
      .insertOne(doc)
      .flatMap: res =>
        undo.update(col.deleteOne(JFilters.equal("_id", res.getInsertedId)).void :: _)

  private def persistBilling(
      user: UserDoc,
      billing: Option[SignupBilling],
      trialWindow: TrialWindow,
      undo: Undo
  ): EitherT[F, SignupError, Unit] =
    user.plan match
      case Plan.Free => EitherT.rightT[F, SignupError](())
      case Plan.ProMonthly | Plan.ProYearly =>
        billing match
          case Some(details) =>
            for
              doc <- billingWorkflow.createBilling(user, details, trialWindow)
              enriched = doc.copy(trialEndsAt = trialWindow.endsAt)
              _ <- EitherT.liftF(insertTracked(billingCol, enriched, undo))
            yield ()
          case None => EitherT.leftT[F, Unit](SignupError.BillingRequired)

  private def validatePw(pw: String): Either[SignupError, Unit] =
    val ok =
      pw.length >= MINPWDLEN &&
        pw.exists(_.isLower) &&
        pw.exists(_.isUpper) &&
        pw.exists(_.isDigit)
    if ok then Right(()) else Left(SignupError.BadPassword)

  /** The email local part reduced to lower-case `[a-z0-9._-]`, at most 32 chars, padded to the users validator's 3-char minimum, and kept
    * clear of the prefix that marks a DB-less guest token.
    */
  private def userIdBase(email: String): String =
    val kept   = email.takeWhile(_ != '@').toLowerCase(Locale.ROOT).filter(userIdChars).take(MAXUSERIDBASE)
    val padded = if kept.length < MINUSERID then s"${kept}user" else kept
    if padded.startsWith(GUESTPREFIX) then s"user-$padded" else padded

  private def candidate(base: String, i: Int): String = if i == 0 then base else s"$base$i"

  /** Index of the first candidate not yet taken; a concurrent signup can still claim it before our insert does. */
  private def firstFreeCandidate(base: String): F[Int] =
    def loop(i: Int): F[Int] =
      usersCol
        .count(feq("userid", candidate(base, i)))
        .flatMap:
          case 0 => i.pure[F]
          case _ => loop(i + 1)
    loop(0)

  /** Inserts the user, moving on to the next candidate when the unique userid index reports a collision. */
  private def insertUser(user: UserDoc, base: String, start: Int, undo: Undo): F[Either[SignupError, UserDoc]] =
    def attempt(i: Int): F[Either[SignupError, UserDoc]] =
      val claimed = user.copy(userid = candidate(base, i))
      insertTracked(usersCol, claimed, undo)
        .as(claimed.asRight[SignupError])
        .recoverWith:
          case mw: MongoWriteException if mw.getError.getCategory == DUPLICATE_KEY =>
            val field = Option(mw.getError.getMessage).flatMap(dupKeyField.findFirstMatchIn).map(_.group(1))
            if !field.contains("userid") then SignupError.EmailExists.asLeft[UserDoc].pure[F]
            else if i - start + 1 < MAXUSERIDTRIES then attempt(i + 1)
            else SignupError.UserIdExists.asLeft[UserDoc].pure[F]
    attempt(start)

  override def signup(req: SignupRequest): F[Either[SignupError, SignupOK]] =
    Ref
      .of[F, List[F[Unit]]](Nil)
      .flatMap: undo =>
        val program = for
          _ <- EitherT.fromEither[F](validatePw(req.password))

          email_norm = req.email.trim.toLowerCase

          _ <- EitherT:
            usersCol
              .count(feq("email_norm", email_norm))
              .map:
                case 0 => Right(())
                case _ => Left(SignupError.EmailExists)
          base = userIdBase(req.email)
          start       <- EitherT.liftF(firstFreeCandidate(base))
          hash        <- EitherT.liftF(hasher.hash(req.password))
          token       <- EitherT.liftF(Sync[F].delay(UUID.randomUUID().toString))
          tokenHash   <- EitherT.liftF(Sync[F].delay(DeterministicHash.sha256(token)))
          expiresAt   <- EitherT.liftF(Sync[F].delay(Instant.now.plus(1, ChronoUnit.DAYS)))
          trialWindow <- EitherT.liftF(Sync[F].delay(trialConfig.windowFor(req.plan, Instant.now())))
          user <- EitherT(
            insertUser(
              UserDoc(
                email = req.email,
                email_norm = email_norm,
                passwordHash = hash,
                userid = candidate(base, start),
                plan = req.plan,
                status = trialWindow.statusDuringTrial,
                displayName = req.displayName,
                verificationTokenHash = Some(tokenHash),
                verificationExpires = Some(expiresAt),
                trialEndsAt = trialWindow.endsAt
              ),
              base,
              start,
              undo
            )
          )
          _ <- EitherT.liftF(insertTracked(userProfileCol, UserProfileDoc(user.userid), undo))
          _ <- persistBilling(user, req.billing, trialWindow, undo)
          link = s"${VERIFYHOST}api/v3/auth/verify?token=$token"
          _ <- EitherT.liftF(Sync[F].delay(L.debug(s"saved $token with $tokenHash")))
          _ <- EitherT(
            publishEmailJob(
              EmailJob(
                EmailJob.KindVerifySignup,
                user._id.toHexString,
                correlationId = Some(user.userid)
              )
            ).attempt.map:
              case Right(_) => Right(())
              case Left(_)  => Left(SignupError.BadEmail)
          )
        yield SignupOK(user.userid)

        program.value.attempt.flatMap:
          case Left(e) =>
            cleanupSignup(undo) *> handleSignupException(e)
          case Right(Left(err)) =>
            cleanupSignup(undo) *> (Left(err): Either[SignupError, SignupOK]).pure[F]
          case Right(Right(ok)) =>
            Right(ok).pure[F]

  private def cleanupSignup(undo: Undo): F[Unit] =
    undo.get.flatMap:
      _.traverse_ :
        _.handleErrorWith: e =>
          Sync[F].delay(L.error(s"Failed to clean up a signup insert: ${e.getMessage}", e))

  private def handleSignupException(e: Throwable): F[Either[SignupError, SignupOK]] =
    val known: Option[SignupError] = e match
      case mw: MongoWriteException if mw.getError.getCategory == DUPLICATE_KEY =>
        val msg = Option(mw.getError.getMessage).getOrElse("")
        if msg.contains("email_norm") then Some(SignupError.EmailExists)
        else Some(SignupError.UserIdExists)
      case mw: MongoWriteException
          if mw.getError.getCode == docFail ||
            Option(mw.getError.getMessage).exists(_.startsWith("Document failed validation")) =>
        Some(SignupError.InvalidEmail)
      case _: MessagingException =>
        Some(SignupError.BadEmail)
      case _ =>
        None
    known match
      case Some(err) => err.asLeft[SignupOK].pure[F]
      case None      => Sync[F].delay(L.error(s"Error during signup: ${e.getMessage}", e)) *> e.raiseError
