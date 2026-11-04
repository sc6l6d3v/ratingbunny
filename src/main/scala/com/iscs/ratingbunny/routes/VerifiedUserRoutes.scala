package com.iscs.ratingbunny.routes

import cats.effect.Async
import cats.implicits.*
import com.iscs.ratingbunny.domains.UserRepo
import org.http4s.Response
import org.http4s.dsl.Http4sDsl

trait VerifiedUserRoutes:

  /** Runs `body` for a guest or for a user whose email is verified, and answers 403 to anyone else before `body` does any work. Guests
    * have no user record (their token is all there is), so they are let through without a lookup.
    */
  protected def ensureVerified[F[_]: Async](
      uid: String,
      userRepo: UserRepo[F]
  )(
      body: => F[Response[F]]
  ): F[Response[F]] =
    val dsl = Http4sDsl[F]; import dsl.*

    if uid.startsWith("guest-") then body
    else
      userRepo.findByUserId(uid).flatMap:
        case Some(u) if u.emailVerified => body
        case _                          => Forbidden()
