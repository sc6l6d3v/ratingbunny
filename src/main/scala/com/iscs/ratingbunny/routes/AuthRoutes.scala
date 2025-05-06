package com.iscs.ratingbunny.routes

import cats.effect.{Async, Sync}
import cats.implicits.*
import com.iscs.ratingbunny.domains.SignupError.*
import com.iscs.ratingbunny.domains.{AuthCheck, SignupRequest}
import com.typesafe.scalalogging.Logger
import io.circe.{DecodingFailure, Json}
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.http4s.circe.CirceEntityCodec.{circeEntityDecoder, circeEntityEncoder}
import org.http4s.dsl.Http4sDsl
import org.http4s.{HttpRoutes, InvalidMessageBodyFailure}

object AuthRoutes:
  private val L          = Logger[this.type]
  private val apiVersion = "v3"

  def httpRoutes[F[_]: Async](A: AuthCheck[F]): HttpRoutes[F] =
    val dsl = Http4sDsl[F]
    import dsl.*

    val svc = HttpRoutes.of[F]:
      case req @ POST -> Root / "api" / `apiVersion` / "auth" / "signup" =>
        for
          decoded <- req.attemptAs[SignupRequest].value
          resp <- decoded match
            case Left(InvalidMessageBodyFailure(_, Some(df: DecodingFailure))) =>
              BadRequest(s"Bad signup payload: ${df.message}")

            // Fallback for any DecodeFailure that *doesn't* carry a Circe cause
            case Left(other) => BadRequest(Json.obj("error" -> Json.fromString(other.getMessage)))

            case Right(sr) =>
              for
                _      <- Sync[F].delay(L.info(s""""request" signup=$sr"""))
                result <- A.signup(sr)
                out <- result match
                  case Right(uid)         => Created(Json.obj("userid" -> uid.asJson))
                  case Left(EmailExists)  => Conflict("email exists")
                  case Left(UserIdExists) => Conflict("userid exists")
                  case Left(BadPassword)  => BadRequest("weak password")
                  case Left(BadEmail)     => BadRequest("Bad email")
                  case Left(BadBirthDate) => BadRequest("Bad birthdate")
                  case Left(BadGender)    => BadRequest("Invalid gender")
                  case Left(BadCountry)   => BadRequest("Invalid country")
                  case Left(BadLanguage)  => BadRequest("Invalid language")
                  case Left(BadTimezone)  => BadRequest("Invalid time zone")
              yield out
        yield resp
    CORSSetup.methodConfig(svc)
