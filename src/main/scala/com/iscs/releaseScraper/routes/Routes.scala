package com.iscs.releaseScraper.routes

import cats.effect._
import cats.implicits._
import com.iscs.releaseScraper.domains.{ImdbQuery, ReleaseDatesScraperService}
import com.iscs.releaseScraper.model.Requests.ReqParams
import com.iscs.releaseScraper.model.RouteMessage.RouteMessage
import com.iscs.releaseScraper.model.ScrapeResult.Scrape._
import com.typesafe.scalalogging.Logger
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.middleware.{CORS, CORSConfig}

import scala.concurrent.duration._
import scala.util.Try

object Routes {
  private val L = Logger[this.type]
  private val protos = List("http", "https")
  private val reactDeploys = sys.env.getOrElse("ORIGINS", "localhost")
    .split(",")
    .toList.flatMap(host => protos.map(proto => s"$proto://$host"))
  private val reactOrigin = sys.env.getOrElse("ORIGINPORTS", "3000,8080")
    .split(",")
    .map(port => s"http://localhost:$port")
  private val methods = Set("GET", "POST")
  private def checkOrigin(origin: String): Boolean =
    allowedOrigins.contains(origin)
  private val allowedOrigins = reactOrigin ++ reactDeploys
  private val methodConfig = CORSConfig(
    anyOrigin = false,
    allowCredentials = true,
    maxAge = 1.day.toSeconds,
    allowedMethods = Some(methods),
    allowedOrigins = checkOrigin
  )
  private def RouteNotFound(badVal: String) = RouteMessage(badVal)

  def scrapeRoutes[F[_]: Sync: Concurrent](R: ReleaseDatesScraperService[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}
    import dsl._
    val service = HttpRoutes.of[F] {
      case _ @ GET -> Root / "reldate" / year / month / rating =>
        for {
          _ <- Concurrent[F].delay(L.info(s""""request" date=$year/$month rating=$rating"""))
          ratingVal <- Concurrent[F].delay(Try(rating.toDouble).toOption.getOrElse(5.0D))
          scrape <- Concurrent[F].delay(R.findReleases("rel", year, month, ratingVal))
          respList <- scrape.compile.toList
          resp <- Ok(respList)
        } yield resp
      case _ @ GET -> Root / "top" / year / rating =>
        for {
          _ <- Concurrent[F].delay(L.info(s""""request" date=$year rating=$rating"""))
          ratingVal <- Concurrent[F].delay(Try(rating.toDouble).toOption.getOrElse(5.0D))
          scrape <- Concurrent[F].delay(R.findMovies("top", year, ratingVal))
          respList <- scrape.compile.toList
          resp <- Ok(respList)
        } yield resp
      case _ @ GET -> Root / "new" / year / rating =>
        for {
          _ <- Concurrent[F].delay(L.info(s""""request" date=$year rating=$rating"""))
          ratingVal <- Concurrent[F].delay(Try(rating.toDouble).toOption.getOrElse(5.0D))
          scrape <- Concurrent[F].delay(R.findMovies("new", year, ratingVal))
          respList <- scrape.compile.toList
          resp <- Ok(respList)
        } yield resp
    }
    CORS(service, methodConfig)
  }

  def imdbRoutes[F[_]: Sync: Concurrent](I: ImdbQuery[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}
    import dsl._
    val service = HttpRoutes.of[F] {
      case req@POST -> Root / "title" / title / rating =>
        for {
          reqParams <- req.as[ReqParams]
          _ <- Concurrent[F].delay(L.info(s""""request" title=$title rating=$rating ${reqParams.toString}"""))
          ratingVal <- Concurrent[F].delay(Try(rating.toDouble).toOption.getOrElse(5.0D))
          imdbTitles <- Concurrent[F].delay(I.getByTitle(title, ratingVal, reqParams))
          resp <- Ok(imdbTitles)
        } yield resp
      case req@POST -> Root / "name" / name / rating =>
        for {
          reqParams <- req.as[ReqParams]
          _ <- Concurrent[F].delay(L.info(s""""request" title=$name rating=$rating ${reqParams.toString}"""))
          ratingVal <- Concurrent[F].delay(Try(rating.toDouble).toOption.getOrElse(5.0D))
          imdbNames <- Concurrent[F].delay(I.getByName(name, ratingVal, reqParams))
          resp <- Ok(imdbNames)
        } yield resp
      case GET -> Root / "autoname" / name =>
        for {
          _ <- Concurrent[F].delay(L.info(s""""request" autoname=$name"""))
          imdbNames <- Concurrent[F].delay(I.getAutosuggestName(name))
          resp <- Ok(imdbNames)
        } yield resp
      case GET -> Root / "autotitle" / title =>
        for {
          _ <- Concurrent[F].delay(L.info(s""""request" autotitle=$title"""))
          imdbTitles <- Concurrent[F].delay(I.getAutosuggestTitle(title))
          resp <- Ok(imdbTitles)
        } yield resp
      case default =>
        L.error(s"got bad request: ${default.pathInfo}")
        Ok(RouteNotFound(default.pathInfo).asJson)
    }
    CORS(service, methodConfig)
  }
}
