package com.iscs.ratingbunny.routes

import cats.effect.Async
import cats.implicits.*
import com.iscs.ratingbunny.config.TrialConfig
import com.iscs.ratingbunny.domains.UserRepo
import com.iscs.ratingbunny.model.Requests.ReqParams
import com.iscs.ratingbunny.repos.HistoryRepo
import io.circe.generic.auto.*
import io.circe.syntax.*
import mongo4cats.circe.*
import org.http4s.AuthedRoutes
import org.http4s.circe.CirceEntityCodec.{circeEntityDecoder, circeEntityEncoder}
import org.http4s.circe.jsonOf
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.impl.OptionalQueryParamDecoderMatcher
import org.http4s.server.AuthMiddleware
import org.http4s.HttpRoutes

object HistoryRoutes extends VerifiedUserRoutes:
  private object LimitParam extends OptionalQueryParamDecoderMatcher[Int]("limit")

  private def normalizeLimit(raw: Option[Int]): Either[String, Int] =
    raw match
      case Some(value) if value > 0 => Right(value)
      case Some(_)                  => Left("limit must be positive")
      case None                     => Right(10)

  def authedRoutes[F[_]: Async](
      historyRepo: HistoryRepo[F],
      userRepo: UserRepo[F],
      trialConfig: TrialConfig,
      authMw: AuthMiddleware[F, String]
  ): HttpRoutes[F] =
    val dsl = Http4sDsl[F]; import dsl.*

    given org.http4s.EntityDecoder[F, ReqParams] = jsonOf[F, ReqParams]

    val svc = AuthedRoutes.of[String, F]:
      case GET -> Root / "history" / "latest" :? LimitParam(limitOpt) as userId =>
        ensureVerified(userId, trialConfig, userRepo) {
          normalizeLimit(limitOpt).fold(
            msg => BadRequest(msg),
            limit =>
              historyRepo
                .latest(userId, limit)
                .compile
                .toList
                .flatMap(docs => Ok(docs.asJson))
          )
        }

      case GET -> Root / "history" / "popular" :? LimitParam(limitOpt) as userId =>
        ensureVerified(userId, trialConfig, userRepo) {
          normalizeLimit(limitOpt).fold(
            msg => BadRequest(msg),
            limit =>
              historyRepo
                .popular(userId, limit)
                .compile
                .toList
                .flatMap(docs => Ok(docs.asJson))
          )
        }

      case req @ POST -> Root / "history" / "byparams" as userId =>
        ensureVerified(userId, trialConfig, userRepo) {
          for
            params <- req.req.as[ReqParams]
            docOpt <- historyRepo.byParams(userId, params)
            resp <- docOpt.fold(NotFound())(doc => Ok(doc.asJson))
          yield resp
        }

      case GET -> Root / "history" / "bysig" / sig as userId =>
        ensureVerified(userId, trialConfig, userRepo) {
          historyRepo.getUsers(sig).flatMap {
            case Some(doc) => Ok(doc.asJson)
            case None      => NotFound()
          }
        }

    CORSSetup.methodConfig(authMw(svc))
