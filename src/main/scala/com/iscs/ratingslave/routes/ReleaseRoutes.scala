package com.iscs.ratingslave.routes

import cats.effect.{Async, Sync}
import cats.implicits._
import com.iscs.ratingslave.domains.ReleaseDates
import com.iscs.ratingslave.util.DecodeUtils.getRating
import com.typesafe.scalalogging.Logger
import io.circe.generic.auto._
import org.http4s.circe.CirceEntityCodec.circeEntityEncoder
import org.http4s.MediaType.application._
import org.http4s.MediaType.image._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`Content-Type`

object ReleaseRoutes {
  private val L = Logger[this.type]

  def httpRoutes[F[_] : Async](R: ReleaseDates[F]): HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl._
    val imgSvc = HttpRoutes.of[F] {
      case _@GET -> Root / "api" / "v1" / "image" / imdbId =>
        for {
          _ <- Sync[F].delay(L.info(s""""request" image=$imdbId"""))
          resp <- Ok(R.getImage(imdbId))
        } yield resp
    }.map(_.withContentType(`Content-Type`(`jpeg`)))
    val metaSvc = HttpRoutes.of[F] {
      case _ @ GET -> Root / "api" / "v1" / "reldate" / year / month / rating =>
        for {
          _ <- Sync[F].delay(L.info(s""""request" date=$year/$month rating=$rating"""))
          ratingVal <- getRating(rating)
          resp <- Ok(R.findReleases("rel", year, month, ratingVal))
        } yield resp
       case _ @ GET -> Root / "api" / "v1" / "top" / year / rating =>
        for {
          _ <- Sync[F].delay(L.info(s""""request" date=$year rating=$rating"""))
          ratingVal <- getRating(rating)
          resp <- Ok(R.findMovies("top", year, ratingVal))
        } yield resp
      case _ @ GET -> Root / "api" / "v1" / "new" / year / rating =>
        for {
          _ <- Sync[F].delay(L.info(s""""request" date=$year rating=$rating"""))
          ratingVal <- getRating(rating)
          resp <- Ok(R.findMovies("new", year, ratingVal))
        } yield resp
/*      case default =>
        L.error(s"got bad request: ${default.pathInfo} ")
        Ok(RouteNotFound(default.pathInfo.toString))*/
    }.map(_.withContentType(`Content-Type`(`json`)))
    CORSSetup.methodConfig(imgSvc <+> metaSvc)
  }
}
