package com.iscs.ratingbunny.routes

import cats.effect.Async
import cats.implicits.*
import com.iscs.ratingbunny.config.TrialConfig
import com.iscs.ratingbunny.domains.UserRepo
import org.http4s.Response
import org.http4s.dsl.Http4sDsl

trait VerifiedUserRoutes:

  protected def ensureVerified[F[_]: Async](
      uid: String,
      trialConfig: TrialConfig,
      userRepo: UserRepo[F]
  )(
      body: => F[Response[F]]
  ): F[Response[F]] =
    val dsl = Http4sDsl[F]; import dsl.*

    if trialConfig.enabled then body
    else
      userRepo.findByUserId(uid).flatMap {
        case Some(u) if u.emailVerified => body
        case _                          => Forbidden()
      }
