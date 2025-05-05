package com.iscs.ratingbunny.domains

import cats.Parallel
import cats.data.EitherT
import cats.effect.*
import cats.effect.implicits.*
import cats.effect.kernel.Clock
import cats.implicits.*
import com.iscs.ratingbunny.model.Requests.ReqParams
import com.iscs.ratingbunny.util.{DecodeUtils, PasswordHasher}
import com.mongodb.MongoWriteException
import com.mongodb.ErrorCategory.DUPLICATE_KEY
import fs2.Stream
import io.circe.generic.auto.*
import mongo4cats.circe.*
import mongo4cats.collection.MongoCollection
import org.http4s.circe.*
import org.http4s.client.Client
import org.http4s.{Method, Request, Uri}

import scala.concurrent.duration.FiniteDuration
import scala.language.implicitConversions

trait AuthCheck[F[_]]:
  def signup(req: SignupRequest): F[Either[SignupError, String]] // returns userid

enum SignupError:
  case EmailExists
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
    hasher: PasswordHasher[F] // see §4.3
) extends AuthCheck[F] with QuerySetup:

  private def genUserId(base: String): F[String] =
    val seed = base.takeWhile(_ != '@')
    def loop(i: Int): F[String] = {
      val candidate = if (i == 0) seed else s"$seed$i"
      usersCol.count(feq("userid", candidate)).flatMap {
        case 0 => candidate.pure[F]
        case _ => loop(i + 1)
      }
    }
    loop(0)

  override def signup(req: SignupRequest): F[Either[SignupError, String]] =
    (for
      // EitherT gives you an Either along the way
      _ <- EitherT {
        usersCol.count(feq("email", req.email)).map {
          case 0 => Right(())
          case _ => Left(SignupError.EmailExists)
        }
      }
      uid  <- EitherT.liftF(genUserId(req.email))
      hash <- EitherT.liftF(hasher.hash(req.password))
      user = UserDoc(
        email = req.email,
        passwordHash = hash,
        userid = uid,
        plan = req.plan,
        status = SubscriptionStatus.Active,
        displayName = req.displayName
      )
      prof = UserProfileDoc(uid)
      _ <- EitherT.liftF(usersCol.insertOne(user))
      _ <- EitherT.liftF(userProfileCol.insertOne(prof))
    yield uid).value // F[Either[SignupError, String]]
      .handleError {
        case mw: MongoWriteException if mw.getError.getCategory == DUPLICATE_KEY =>
          Left(SignupError.UserIdExists)
        case _ =>
          Left(SignupError.BadPassword) // or a generic failure
      }
