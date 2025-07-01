package com.iscs.ratingbunny.security

import cats.effect.*
import cats.syntax.functor.*
import cats.syntax.flatMap.* // Add this import
import cats.data.{Kleisli, OptionT}
import org.http4s.{AuthedRoutes, Request, Response}
import org.http4s.server.AuthMiddleware
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Authorization
import com.typesafe.scalalogging.Logger
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}

object JwtAuth:
  private val L = Logger[this.type]

  /** Manual JWT validation using pdi.jwt */
  def validateJwt[F[_]: Sync](token: String, secretKey: String): F[Option[String]] =
    Sync[F].delay {
      JwtCirce.decodeAll(token, secretKey, Seq(JwtAlgorithm.HS256)) match {
        case scala.util.Success((_, claim, _)) =>
          L.debug(s"JWT validation successful for subject: ${claim.subject}")
          claim.subject
        case scala.util.Failure(ex) =>
          L.debug(s"JWT validation failed: ${ex.getMessage}")
          None
      }
    }

    /** Extract Bearer token from Authorization header */
  def extractBearerToken[F[_]](request: Request[F]): Option[String] =
    request.headers.get[Authorization].flatMap {
      case Authorization(org.http4s.Credentials.Token(org.http4s.AuthScheme.Bearer, token)) => Some(token)
      case _                                                                                => None
    }

  /** Create AuthMiddleware using pdi.jwt */
  def middleware[F[_]: Sync](secretKey: String): AuthMiddleware[F, String] =
    val dsl = Http4sDsl[F]
    import dsl._

    val onFailure: AuthedRoutes[String, F] = Kleisli { req =>
      OptionT.pure(Response[F](Unauthorized).withEntity("Unauthorized"))
    }

    val authUser: Kleisli[F, Request[F], Either[String, String]] =
      Kleisli { request =>
        extractBearerToken(request) match {
          case Some(token) =>
            validateJwt(token, secretKey).map {
              case Some(userId) =>
                L.debug(s"Authentication successful for user: $userId")
                Right(userId)
              case None =>
                L.debug("JWT validation failed")
                Left("Invalid JWT token")
            }
          case None =>
            L.debug("No Bearer token found")
            Sync[F].pure(Left("No Authorization header"))
        }
      }

    AuthMiddleware(authUser, onFailure)
