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
import org.http4s.{
  headers,
  AuthScheme,
  AuthedRoutes,
  Credentials,
  EntityDecoder,
  EntityEncoder,
  HttpRoutes,
  InvalidMessageBodyFailure,
  Request,
  Response
}
import org.http4s.server.AuthMiddleware
import java.time.Instant
import org.http4s.dsl.impl.QueryParamDecoderMatcher

object AuthRoutes:
  private val L = Logger[this.type]

  def httpRoutes[F[_]: Async](
      A: AuthCheck[F],
      Login: AuthLogin[F],
      userRepo: UserRepo[F],
      token: TokenIssuer[F],
      passwordReset: PasswordResetService[F]
  ): HttpRoutes[F] =
    val dsl = Http4sDsl[F]
    import dsl.*

    given EntityDecoder[F, RegisterReq]                 = jsonOf
    given EntityDecoder[F, LoginReq]                    = jsonOf
    given EntityDecoder[F, PasswordResetRequest]        = jsonOf
    given EntityDecoder[F, PasswordResetConfirmRequest] = jsonOf
    given EntityEncoder[F, TokenPair]                   = jsonEncoderOf

    def bearer(req: Request[F]): Option[String] =
      req.headers
        .get[headers.Authorization]
        .collect:
          case headers.Authorization(Credentials.Token(AuthScheme.Bearer, v)) =>
            v

    def requestIp(req: Request[F]): Option[String] =
      req.remote.map(_.host.toUriString)

    object TokenParamMatcher extends QueryParamDecoderMatcher[String]("token")

    val svc = HttpRoutes.of[F]:
      case req @ POST -> Root / "auth" / "signup" =>
        for
          decoded <- req.attemptAs[SignupRequest].value
          resp <- decoded match
            case Left(InvalidMessageBodyFailure(details, Some(df: DecodingFailure))) =>
              BadRequest(s"Bad signup payload: ${df.message} from $details")

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
                        "message" -> "verification email sent".asJson
                      )
                    )
                  case Left(EmailExists)     => Conflict("email exists")
                  case Left(InvalidEmail)    => BadRequest("invalid email")
                  case Left(UserIdExists)    => Conflict("userid exists")
                  case Left(BadPassword)     => BadRequest("weak password")
                  case Left(BadEmail)        => BadRequest("Bad email")
                  case Left(BadBirthDate)    => BadRequest("Bad birthdate")
                  case Left(BadGender)       => BadRequest("Invalid gender")
                  case Left(BadCountry)      => BadRequest("Invalid country")
                  case Left(BadLanguage)     => BadRequest("Invalid language")
                  case Left(BadTimezone)     => BadRequest("Invalid time zone")
                  case Left(BillingRequired) => BadRequest("billing info required for pro plan")
                  case Left(BillingFailed(_)) =>
                    InternalServerError(Json.obj("error" -> Json.fromString("failed to provision billing")))
              yield out
        yield resp
      case req @ POST -> Root / "auth" / "login" =>
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
                  case Left(LoginError.Unverified) =>
                    Forbidden(Json.obj("error" -> Json.fromString("email not verified")))
        yield resp
      case req @ POST -> Root / "auth" / "password-reset" / "request" =>
        req
          .attemptAs[PasswordResetRequest]
          .value
          .flatMap:
            case Left(df) =>
              BadRequest(Json.obj("error" -> Json.fromString(df.getMessage)))
            case Right(pr) =>
              passwordReset.requestReset(pr, requestIp(req)).attempt *> Accepted(Json.obj("status" -> "ok".asJson))
      case req @ POST -> Root / "auth" / "password-reset" / "confirm" =>
        req
          .attemptAs[PasswordResetConfirmRequest]
          .value
          .flatMap:
            case Left(df) => BadRequest(Json.obj("error" -> Json.fromString(df.getMessage)))
            case Right(pr) =>
              passwordReset
                .confirmReset(pr)
                .flatMap:
                  case Right(_) => Ok(Json.obj("status" -> "ok".asJson))
                  case Left(_)  => BadRequest(Json.obj("error" -> Json.fromString("invalid or expired token")))
      case ((POST | GET) -> Root / "auth" / "verify") :? TokenParamMatcher(strToken) =>
        for
          userOpt <- userRepo.findByVerificationToken(strToken)
          now     <- Sync[F].delay(Instant.now)
          resp <- userOpt match
            case Some(u) if u.verificationExpires.forall(_.isAfter(now)) =>
              userRepo.markEmailVerified(u.userid) *>
                token
                  .issue(u.copy(emailVerified = true, verificationTokenHash = None, verificationExpires = None))
                  .flatMap: tp =>
                    Ok(Json.obj("access" -> tp.access.asJson, "refresh" -> tp.refresh.asJson))
            case _ =>
              L.error(s"Error during verify: token not found")
              Forbidden(Json.obj("error" -> Json.fromString("invalid or expired token")))
        yield resp
      case POST -> Root / "auth" / "guest" =>
        for
          uid  <- Sync[F].delay(java.util.UUID.randomUUID().toString).map(u => s"guest-$u")
          tp   <- token.issueGuest(uid)
          resp <- Ok(Json.obj("access" -> tp.access.asJson, "refresh" -> tp.refresh.asJson))
        yield resp
      case req @ POST -> Root / "auth" / "refresh" =>
        bearer(req).fold(Forbidden()): tok =>
          token
            .rotate(tok)
            .flatMap:
              case None    => Forbidden()
              case Some(p) => Ok(p)
      case req @ POST -> Root / "auth" / "logout" =>
        bearer(req).fold(Forbidden())(tok => token.revoke(tok) *> NoContent())
    CORSSetup.methodConfig(svc)

  def authedRoutes[F[_]: Async](
      userRepo: UserRepo[F],
      trialService: TrialService[F],
      authMw: AuthMiddleware[F, String]
  ): HttpRoutes[F] =
    val dsl = Http4sDsl[F]
    import dsl.*

    val svc = AuthedRoutes.of[String, F]:
      case GET -> Root / "auth" / "me" as userId =>
        userRepo
          .findByUserId(userId)
          .flatMap:
            case Some(u) if u.emailVerified =>
              val info = UserInfo(u.userid, u.email, u.plan.asString, u.displayName)
              Ok(info.asJson)
            case Some(_) => Forbidden()
            case None    => NotFound()
      case POST -> Root / "auth" / "trial" / "cancel" as userId =>
        trialService
          .cancelTrial(userId)
          .flatMap:
            case Right(_)                              => NoContent()
            case Left(CancelTrialFailure.UserNotFound) => NotFound(Json.obj("error" -> Json.fromString("user not found")))
            case Left(CancelTrialFailure.NotTrialing) =>
              BadRequest(Json.obj("error" -> Json.fromString("not in trial")))
            case Left(CancelTrialFailure.TrialExpired) =>
              BadRequest(Json.obj("error" -> Json.fromString("trial already ended")))
            case Left(CancelTrialFailure.Billing(err)) =>
              InternalServerError(Json.obj("error" -> Json.fromString(err.message)))

    CORSSetup.methodConfig(authMw(svc))
