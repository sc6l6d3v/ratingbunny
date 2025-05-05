package com.iscs.ratingbunny.routes

import cats.effect.{Async, Sync}
import cats.implicits.*
import com.iscs.ratingbunny.domains.{AuthCheck, SignupRequest}
import com.iscs.ratingbunny.domains.SignupError.*
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.http4s.circe.CirceEntityCodec.{circeEntityDecoder, circeEntityEncoder}
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import com.typesafe.scalalogging.Logger
import io.circe.Json

object AuthRoutes:
  private val L          = Logger[this.type]
  private val apiVersion = "v3"

  def httpRoutes[F[_]: Async](A: AuthCheck[F]): HttpRoutes[F] =
    val dsl = Http4sDsl[F]
    import dsl.*

    val svc = HttpRoutes.of[F]:
      case req @ POST -> Root / "api" / `apiVersion` / "auth" / "signup" =>
        for
          sr     <- req.as[SignupRequest]
          _      <- Sync[F].delay(L.info(s""""request" signup=$sr"""))
          result <- A.signup(sr)
          resp <- result match
            case Right(uid) =>
              Created(Json.obj("userid" -> uid.asJson))
            case Left(EmailExists)  => Conflict("email exists")
            case Left(UserIdExists) => Conflict("userid exists")
            case Left(BadPassword)  => BadRequest("weak password")
            case Left(BadEmail)     => BadRequest("Bad email")
            case Left(BadBirthDate) => BadRequest("Bad birthdate")
            case Left(BadGender)    => BadRequest("Invalid gender")
            case Left(BadCountry)   => BadRequest("Invalid country")
            case Left(BadLanguage)  => BadRequest("Invalid language")
            case Left(BadTimezone)  => BadRequest("Invalid time zone")
        yield resp
    CORSSetup.methodConfig(svc)
