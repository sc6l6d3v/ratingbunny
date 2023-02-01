package com.iscs.ratingslave.routes

import cats.effect._
import cats.implicits._
import com.iscs.ratingslave.domains.ImdbQuery
import com.iscs.ratingslave.model.Requests._
import com.iscs.ratingslave.util.DecodeUtils.getRating
import com.typesafe.scalalogging.Logger
import fs2.Stream
import io.circe.generic.auto._
import org.http4s.HttpRoutes
import org.http4s.MediaType.application._
import org.http4s.circe.CirceEntityCodec.{circeEntityDecoder, circeEntityEncoder}
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`Content-Type`

object ImdbRoutes {
  private val L = Logger[this.type]

  def httpRoutes[F[_]: Async](I: ImdbQuery[F]): HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl._
    val svc = HttpRoutes.of[F] {
      case req@POST -> Root / "api" / "v1" / "title" / rating =>
        for {
          reqParams <- req.as[ReqParams]
          rtng <- getRating(rating)
          titleStreamResp <- Ok(if (reqParams.year.nonEmpty)
            I.getByTitle(reqParams.query, rtng, reqParams)
          else
            Stream.empty)
        } yield titleStreamResp
      case req@POST -> Root / "api" / "v1" / "name2" / name / rating =>
        for {
          reqParams <- req.as[ReqParams]
          rtng <- getRating(rating)
          imdbNames <- Ok(if (reqParams.year.nonEmpty)
            I.getByName(name, rtng, reqParams)
          else
            Stream.empty)
        } yield imdbNames
      case req@POST -> Root / "api" / "v1" / "name" / name / rating =>
        for {
          reqParams <- req.as[ReqParams]
          rtng <- getRating(rating)
          imdbNames <- Ok(if (reqParams.year.nonEmpty)
            I.getByEnhancedName(name, rtng, reqParams)
          else
            Stream.empty)
        } yield imdbNames
      case GET -> Root / "api" / "v1" / "autoname" / name =>
        for {
          _ <- Sync[F].delay(L.info(s""""request" autoname=$name"""))
          resp <- Ok(I.getAutosuggestName(name))
        } yield resp
      case GET -> Root / "api" / "v1" / "autotitle" / title =>
        for {
          _ <- Sync[F].delay(L.info(s""""request" autotitle=$title"""))
          resp <- Ok(I.getAutosuggestTitle(title))
        } yield resp
    }.map(_.withContentType(`Content-Type`(`json`)))
    CORSSetup.methodConfig(svc)
  }
}
