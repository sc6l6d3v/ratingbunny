package com.iscs.ratingslave.routes

import cats.effect._
import cats.implicits._
import com.iscs.ratingslave.domains.ImdbQuery
import com.iscs.ratingslave.dslparams.{WindowHeightQueryParameterMatcher, WindowWidthQueryParameterMatcher}
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

import scala.util.Try

object ImdbRoutes {
  private val L = Logger[this.type]
  private val cardWidth = 250
  private val cardHeight = cardWidth * 2 / 3
  private val rowSpacer = 80
  private val columnSpacer = 50
  private val rowsPerPage = 3 // function of window height
  private def cardsPerRow(width: Int): Int = width / (cardWidth + columnSpacer)
  private def pageSize(width: Int) = cardsPerRow(width) * rowsPerPage

  def httpRoutes[F[_]: Async](I: ImdbQuery[F]): HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl._
    val svc = HttpRoutes.of[F] {
      case req@POST -> Root / "api" / "v2" / "title" / page / rating :? WindowWidthQueryParameterMatcher(ws) +& WindowHeightQueryParameterMatcher(wh) =>
        for {
          _ <- Sync[F].delay(L.debug(s""""params" ws=$ws wh=$wh"""))
          reqParams <- req.as[ReqParams]
          rtng <- getRating(rating)
          titleStream <- Sync[F].delay(if (reqParams.year.nonEmpty)
            I.getByTitle(reqParams.query, rtng, reqParams)
          else
            Stream.empty)
          titleList <- titleStream.compile.toList
          pg <- Sync[F].delay(Try(page.toInt).toOption.getOrElse(1))
          wsInt <- Sync[F].delay(Try(ws.toInt).toOption.getOrElse(600))
          pgs <- Sync[F].delay(pageSize(wsInt))
          portionTitleList <- Sync[F].delay(titleList.slice((pg - 1) * pgs, (pg - 1) * pgs + pgs))
          resp <- Ok(portionTitleList)
        } yield resp
      case req@POST -> Root / "api" / "v2" / "name2" / name / rating =>
        for {
          reqParams <- req.as[ReqParams]
          rtng <- getRating(rating)
          imdbNameStream <- Sync[F].delay(if (reqParams.year.nonEmpty)
            I.getByName(name, rtng, reqParams)
          else
            Stream.empty)
          nameList <- imdbNameStream.compile.toList
          resp <- Ok(nameList)
        } yield resp
      case req@POST -> Root / "api" / "v2" / "name" / page / rating :? WindowWidthQueryParameterMatcher(ws) +& WindowHeightQueryParameterMatcher(wh) =>
        for {
          reqParams <- req.as[ReqParams]
          rtng <- getRating(rating)
          imdbNameStream <- Sync[F].delay(if (reqParams.year.nonEmpty)
            I.getByEnhancedName(reqParams.query.getOrElse("Joe Blow"), rtng, reqParams)
          else
            Stream.empty)
          nameList <- imdbNameStream.compile.toList
          pg <- Sync[F].delay(Try(page.toInt).toOption.getOrElse(1))
          wsInt <- Sync[F].delay(Try(ws.toInt).toOption.getOrElse(600))
          pgs <- Sync[F].delay(pageSize(wsInt))
          portionNameList <- Sync[F].delay(nameList.slice((pg - 1) * pgs, (pg - 1) * pgs + pgs))
          resp <- Ok(portionNameList)
        } yield resp
      case GET -> Root / "api" / "v2" / "autoname" / name =>
        for {
          _ <- Sync[F].delay(L.info(s""""request" autoname=$name"""))
          nameStream <- Sync[F].delay(I.getAutosuggestName(name))
          nameList <- nameStream.compile.toList
          resp <- Ok(nameList)
        } yield resp
      case GET -> Root / "api" / "v2" / "autotitle" / title =>
        for {
          _ <- Sync[F].delay(L.info(s""""request" autotitle=$title"""))
          titleStream <- Sync[F].delay(I.getAutosuggestTitle(title))
          titleList <- titleStream.compile.toList
          resp <- Ok(titleList)
        } yield resp
    }.map(_.withContentType(`Content-Type`(`json`)))
    CORSSetup.methodConfig(svc)
  }
}
