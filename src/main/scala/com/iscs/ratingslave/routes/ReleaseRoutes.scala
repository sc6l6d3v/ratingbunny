package com.iscs.ratingslave.routes

import cats.effect.Sync
import com.iscs.ratingslave.domains.ReleaseDates
import com.iscs.ratingslave.util.DecodeUtils.getRating
import com.typesafe.scalalogging.Logger
import fs2.Stream
import org.http4s.MediaType.application._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`Content-Type`
import zio.json.EncoderOps

object ReleaseRoutes {
  private val L = Logger[this.type]

  def httpRoutes[F[_] : Sync](R: ReleaseDates[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._
    HttpRoutes.of[F] {
      case _ @ GET -> Root / "api" / "v1" / "reldate" / year / month / rating =>
        Ok(for {
          _ <- Stream.eval(Sync[F].delay(L.info(s""""request" date=$year/$month rating=$rating""")))
          ratingVal <- Stream.eval(getRating(rating))
          releases <- R.findReleases("rel", year, month, ratingVal)
          relAsJson <- Stream.eval(Sync[F].delay(releases.toJson))
        } yield relAsJson)
       case _ @ GET -> Root / "api" / "v1" / "top" / year / rating =>
        Ok(for {
          _ <- Stream.eval(Sync[F].delay(L.info(s""""request" date=$year rating=$rating""")))
          ratingVal <- Stream.eval(getRating(rating))
          resp <- R.findMovies("top", year, ratingVal)
          scrapeJson <- Stream.eval(Sync[F].delay(resp.toJson))
        } yield scrapeJson)
      case _ @ GET -> Root / "api" / "v1" / "new" / year / rating =>
        Ok(for {
          _ <- Stream.eval(Sync[F].delay(L.info(s""""request" date=$year rating=$rating""")))
          ratingVal <- Stream.eval(getRating(rating))
          resp <- R.findMovies("new", year, ratingVal)
          scrapeJson <- Stream.eval(Sync[F].delay(resp.toJson))
        } yield scrapeJson)
    }.map(_.withContentType(`Content-Type`(`json`)))
  }
}
