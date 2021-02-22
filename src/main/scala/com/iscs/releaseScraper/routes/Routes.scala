package com.iscs.releaseScraper.routes

import cats.effect._
import cats.implicits._
import com.iscs.releaseScraper.domains.ReleaseDatesScraperService
import com.iscs.releaseScraper.model.ScrapeResult.Scrape._
import com.iscs.releaseScraper.model.ScrapeResult.ScrapeList
import com.typesafe.scalalogging.Logger
import fs2.Stream
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.middleware.{CORS, CORSConfig}

import scala.concurrent.duration._
import scala.util.Try

object Routes {
  private val L = Logger[this.type]

  private val reactOrigin = "http://localhost:3000"
  private val reactDeploys = List("http://localhost",
    "https://localhost",
    "http://berne.iscs-i.com",
    "https://berne.iscs-i.com",
    "https://192.168.4.46",
    "http://192.168.4.46")
  private val methods = Set("GET")
  private def checkOrigin(origin: String): Boolean =
    allowedOrigins.contains(origin)
  private val allowedOrigins = Set(reactOrigin) ++ reactDeploys
  private val methodConfig = CORSConfig(
    anyOrigin = false,
    allowCredentials = true,
    maxAge = 1.day.toSeconds,
    allowedMethods = Some(methods),
    allowedOrigins = checkOrigin
  )

  def scrapeRoutes[F[_]: Sync: Concurrent](R: ReleaseDatesScraperService[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}
    import dsl._
    val service = HttpRoutes.of[F] {
      case _ @ GET -> Root / "reldate" / year / month / rating =>
        for {
          _ <- Concurrent[F].delay(L.info(s""""request" date=$year/$month rating=$rating"""))
          ratingVal <- Concurrent[F].delay(Try(rating.toDouble).toOption.getOrElse(5.0D))
          scrape <- Concurrent[F].delay(R.scrapeLink(year, month, ratingVal))
          respList <- scrape.compile.toList
          resp <- Ok(respList)
        } yield resp
    }
    CORS(service, methodConfig)
  }
}
