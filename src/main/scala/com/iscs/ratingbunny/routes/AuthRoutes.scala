package com.iscs.ratingbunny.routes

import cats.effect.{Async, Sync}
import cats.implicits.*
import com.iscs.ratingbunny.domains.SignupError.*
import com.iscs.ratingbunny.domains.*
import com.typesafe.scalalogging.Logger
import io.circe.generic.auto.*
import io.circe.syntax.*
import io.circe.{DecodingFailure, Json}
import org.http4s.circe.CirceEntityCodec.{circeEntityDecoder, circeEntityEncoder}
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.dsl.Http4sDsl
import org.http4s.{headers, AuthScheme, Credentials, EntityDecoder, EntityEncoder, HttpRoutes, InvalidMessageBodyFailure, Request, Response}
import tsec.passwordhashers.PasswordHash
import tsec.passwordhashers.jca.*

object AuthRoutes:
  private val L          = Logger[this.type]
  private val apiVersion = "v3"

  def httpRoutes[F[_]: Async](A: AuthCheck[F], Login: AuthLogin[F], userRepo: UserRepo[F], token: TokenIssuer[F]): HttpRoutes[F] =
    val dsl = Http4sDsl[F]
    import dsl.*

    given EntityDecoder[F, RegisterReq] = jsonOf
    given EntityDecoder[F, LoginReq]    = jsonOf
    given EntityEncoder[F, TokenPair]   = jsonEncoderOf

    def verifyPassword(plain: String, hashed: String): F[Boolean] =
      BCrypt.checkpwBool[F](plain, PasswordHash[BCrypt](hashed))

    def hashPassword(plain: String): F[String] =
      BCrypt.hashpw[F](plain).map(identity)

    def bearer(req: Request[F]): Option[String] =
      req.headers.get[headers.Authorization].collect { case headers.Authorization(Credentials.Token(AuthScheme.Bearer, v)) =>
        v
      }

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
                _              <- Sync[F].delay(L.info(s""""request" signup=$sr"""))
                eitherSignupOk <- A.signup(sr)
                out <- eitherSignupOk match
                  case Right(ok) =>
                    Created(
                      Json.obj(
                        "userid"  -> ok.userid.asJson,
                        "userid"  -> ok.userid.asJson,
                        "refresh" -> ok.tokens.refresh.asJson
                      )
                    )
                  case Left(EmailExists)  => Conflict("email exists")
                  case Left(InvalidEmail) => BadRequest("invalid email")
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
      case req @ POST -> Root / "api" / apiVersion / "auth" / "login" =>
        for
          decoded <- req.attemptAs[LoginRequest].value
          resp <- decoded match
            case Left(df) => BadRequest(Json.obj("error" -> Json.fromString(df.getMessage)))
            case Right(lr) =>
              Login
                .login(lr)
                .flatMap:
                  case Right(ok) =>
                    Ok(
                      Json.obj(
                        "userid"  -> ok.userid.asJson,
                        "access"  -> ok.tokens.access.asJson,
                        "refresh" -> ok.tokens.refresh.asJson
                      )
                    )
                  case Left(LoginError.UserNotFound) =>
                    Sync[F].delay(
                      Response(Unauthorized) // empty 401 response
                        .withEntity(Json.obj("error" -> Json.fromString("user not found")))
                    )
                  case Left(LoginError.BadPassword) =>
                    Sync[F].delay(
                      Response(Unauthorized)
                        .withEntity(Json.obj("error" -> Json.fromString("wrong password")))
                    )
                  case Left(LoginError.Inactive) =>
                    Forbidden(Json.obj("error" -> Json.fromString("account inactive")))
        yield resp
      case req @ POST -> Root / "api" / apiVersion / "auth" / "refresh" =>
        bearer(req).fold(Forbidden()) { tok =>
          token.rotate(tok).flatMap {
            case None    => Forbidden()
            case Some(p) => Ok(p)
          }
        }
      case req @ POST -> Root / "api" / apiVersion / "auth" / "logout" =>
        bearer(req).fold(Forbidden())(tok => token.revoke(tok) *> NoContent())
    CORSSetup.methodConfig(svc)
