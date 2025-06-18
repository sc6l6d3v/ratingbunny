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
    for
      userOpt <- usersCol
        .find(feq("email", req.email))
        .first

      result <- userOpt match
        case None =>
          LoginError.UserNotFound.asLeft.pure[F]

        case Some(u) if u.status != SubscriptionStatus.Active =>
          LoginError.Inactive.asLeft.pure[F]

        case Some(u) =>
          hasher
            .verify(req.password, u.passwordHash)
            .flatMap:
              case true  => tokenIssuer.issue(u).map(tp => LoginOK(u.userid, tp).asRight)
              case false => LoginError.BadPassword.asLeft.pure[F]
    yield result
