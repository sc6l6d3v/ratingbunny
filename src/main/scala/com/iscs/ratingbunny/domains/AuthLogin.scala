package com.iscs.ratingbunny.domains

import cats.effect.*
import cats.effect.implicits.*
import cats.implicits.*
import com.iscs.ratingbunny.util.PasswordHasher
import mongo4cats.circe.*
import mongo4cats.collection.MongoCollection

import scala.language.implicitConversions

trait AuthLogin[F[_]]:
  def login(req: LoginRequest): F[Either[LoginError, LoginOK]]

final class AuthLoginImpl[F[_]: Async](
    usersCol: MongoCollection[F, UserDoc],
    hasher: PasswordHasher[F],
    tokenIssuer: TokenIssuer[F]
) extends AuthLogin[F] with QuerySetup:

  override def login(req: LoginRequest): F[Either[LoginError, LoginOK]] =
    val emailNorm = req.email.trim.toLowerCase
    for
      userOpt <- usersCol
        .find(feq("email_norm", emailNorm))
        .first

      result <- userOpt match
        case None =>
          LoginError.UserNotFound.asLeft.pure[F]

        case Some(u) if !AuthLoginImpl.isLoginAllowed(u.status) =>
          LoginError.Inactive.asLeft.pure[F]

        case Some(u) if !u.emailVerified =>
          LoginError.Unverified.asLeft.pure[F]

        case Some(u) =>
          hasher
            .verify(req.password, u.passwordHash)
            .flatMap:
              case true  => tokenIssuer.issue(u).map(tp => LoginOK(u.userid, tp).asRight)
              case false => LoginError.BadPassword.asLeft.pure[F]
    yield result

object AuthLoginImpl:
  private[ratingbunny] def isLoginAllowed(status: SubscriptionStatus): Boolean =
    status match
      case SubscriptionStatus.Active   => true
      case SubscriptionStatus.Trialing => true
      case _                           => false
