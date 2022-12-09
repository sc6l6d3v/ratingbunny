package com.iscs.ratingslave.routes

import com.iscs.ratingslave.model.Requests.ReqParams
import com.iscs.ratingslave.model.RouteMessage.RouteMessage
import com.typesafe.scalalogging.Logger
import org.http4s._
import org.http4s.server.middleware.CORSConfig

import scala.concurrent.duration._

object Routes { // TODO - remove
  private val L = Logger[this.type]
  private val protos = List("http", "https")
  private val reactDeploys = sys.env.getOrElse("ORIGINS", "localhost")
    .split(",")
    .toList.flatMap(host => protos.map(proto => s"$proto://$host"))
  private val methods = Set(Method.GET, Method.POST)
  private def checkOrigin(origin: String): Boolean = {
    L.debug(s"checking $origin against ${allowedOrigins.mkString(",")}")
    allowedOrigins.contains(origin)
  }

  private val allowedOrigins = reactDeploys
  private val methodConfig = CORSConfig.default
    .withAnyMethod(false)
    .withAllowCredentials(true)
    .withMaxAge(1.day)
    .withAllowedMethods(Some(methods))
    .withAllowedOrigins(checkOrigin)
/* //TODO - reapply CORS
  private val methodConfig2 = CORSConfig(
    anyOrigin = false,
    allowCredentials = true,
    maxAge = 1.day.toSeconds,
    allowedMethods = Some(methods),
    allowedOrigins = checkOrigin
  )
*/
  private def RouteNotFound(badVal: String) = RouteMessage(badVal)



/*  def scrapeRoutes[F[_]: Sync](R: ReleaseDates[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}
    import dsl._
    val service = HttpRoutes.of[F] {
      case _ @ GET -> Root / "api" / "v1" / "reldate" / year / month / rating =>
        for {
          _ <- Sync[F].delay(L.info(s""""request" date=$year/$month rating=$rating"""))
          ratingVal <- Sync[F].delay(Try(rating.toDouble).toOption.getOrElse(5.0D))
          scrape <- Sync[F].delay(R.findReleases("rel", year, month, ratingVal))
          respList <- scrape.compile.toList
          resp <- Ok(respList)
        } yield resp
      case _ @ GET -> Root / "api" / "v1" / "top" / year / rating =>
        for {
          _ <- Sync[F].delay(L.info(s""""request" date=$year rating=$rating"""))
          ratingVal <- Sync[F].delay(Try(rating.toDouble).toOption.getOrElse(5.0D))
          scrape <- Sync[F].delay(R.findMovies("top", year, ratingVal))
          respList <- scrape.compile.toList
          resp <- Ok(respList)
        } yield resp
      case _ @ GET -> Root / "api" / "v1" / "new" / year / rating =>
        for {
          _ <- Sync[F].delay(L.info(s""""request" date=$year rating=$rating"""))
          ratingVal <- Sync[F].delay(Try(rating.toDouble).toOption.getOrElse(5.0D))
          scrape <- Sync[F].delay(R.findMovies("new", year, ratingVal))
          respList <- scrape.compile.toList
          resp <- Ok(respList)
        } yield resp
    }
    CORS(service, methodConfig)
  }*/

  private def showReqParam(queryType: String, query: Option[String], rating: String, params: ReqParams): Unit =
    L.info(s""""request" $queryType=${query.getOrElse("UNSET")} rating=$rating ${params.toString}""")

/*  def imdbRoutes[F[_]: Monad : Sync](I: ImdbQuery[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}
    import dsl._
    val service = HttpRoutes.of[F] {
      case req@POST -> Root / "api" / "v1" / "title" / rating =>
        for {
          reqParams <- req.as[ReqParams]
          title <- Sync[F].delay(reqParams.query)
          _ <- Sync[F].delay(showReqParam("title", title, rating, reqParams))
          ratingVal <- Sync[F].delay(Try(rating.toDouble).toOption.getOrElse(5.0D))
          imdbTitles <- Sync[F].delay(I.getByTitle(title, ratingVal, reqParams))
          resp <- Ok(imdbTitles)
        } yield resp
      case req@POST -> Root / "api" / "v1" / "name2" / name / rating =>
        for {
          reqParams <- req.as[ReqParams]
          _ = Sync[F].delay(showReqParam("name", Some(name), rating, reqParams))
          imdbNames <- Sync[F].delay(I.getByName(name, Try(rating.toDouble).toOption.getOrElse(5.0D), reqParams))
          resp <- Ok(imdbNames)
        } yield resp
      case req@POST -> Root / "api" / "v1" / "name" / name / rating =>
        for {
          reqParams <- req.as[ReqParams]
          imdbNames2 <- Sync[F].delay(I.getByEnhancedName(name, Try(rating.toDouble).toOption.getOrElse(5.0D), reqParams))
          resp <- Ok(imdbNames2)
        } yield resp
      case GET -> Root / "api" / "v1" / "autoname" / name =>
        for {
          _ <- Sync[F].delay(L.info(s""""request" autoname=$name"""))
          imdbNames <- Sync[F].delay(I.getAutosuggestName(name))
          resp <- Ok(imdbNames)
        } yield resp
      case GET -> Root / "api" / "v1" / "autotitle" / title =>
        for {
          _ <- Sync[F].delay(L.info(s""""request" autotitle=$title"""))
          imdbTitles <- Sync[F].delay(I.getAutosuggestTitle(title))
          resp <- Ok(imdbTitles)
        } yield resp
      case default =>
        L.error(s"got bad request: ${default.pathInfo}")
        Ok(RouteNotFound(default.pathInfo.toString))
    }
    CORS(service, methodConfig)
  }*/

/*  def emailContactRoutes[F[_]: Sync](E: EmailContact[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}
    import dsl._
    val service = HttpRoutes.of[F] {
      case req@POST -> Root / "api" / "v1" / "addMsg" =>
        for {
          emailParams <- req.as[Email]
          _ <- Sync[F].delay(L.info(s""""request" ${emailParams.toString}"""))
          emailId <- Sync[F].delay(E.saveEmail(
            emailParams.name, emailParams.email,
            emailParams.subject, emailParams.msg))
          resp <- Ok(emailId)
        } yield resp
    }
    CORS(service, methodConfig)
  }*/
}
