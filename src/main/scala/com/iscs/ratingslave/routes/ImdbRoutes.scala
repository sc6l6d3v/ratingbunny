package com.iscs.ratingslave.routes

import cats.effect._
import cats.implicits._
import com.iscs.ratingslave.domains.ImdbQuery
import com.iscs.ratingslave.dslparams._
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

import scala.annotation.tailrec
import scala.util.Try

object ImdbRoutes {
  private val L = Logger[this.type]
  private val rowSpacer = 64
  private val columnSpacer = 64
  private def rowsPerPage2(height: Int, cardHeight: Int): Int = {
    @tailrec
    def rowsPerPageTR(remainingHeight: Int, count: Int): Int = {
      if (remainingHeight < 0) count
      else rowsPerPageTR(remainingHeight - columnSpacer - cardHeight, count + 1)
    }
    rowsPerPageTR(height, 0)
  }
  private def cardsPerRow(width: Int, cardWidth: Int): Int = {
    @tailrec
    def cardsPerRowTR(remainingWidth: Int, count: Int): Int = {
      if (remainingWidth < 0) count
      else cardsPerRowTR(remainingWidth - rowSpacer - cardWidth, count + 1)
    }
    cardsPerRowTR(width, 0)
  }

  private def pageSize(width: Int, cardWidth: Int, height: Int, cardHeight: Int): Int =
    cardsPerRow(width, cardWidth) * rowsPerPage2(height, cardHeight)

  private def dimsFromString(strPairs: List[(String, Int)]): List[Int] = {
    strPairs.map { case (strVal, defInt) =>
      Try(strVal.toInt).toOption.getOrElse(defInt)
    }
  }

  def httpRoutes[F[_]: Async](I: ImdbQuery[F]): HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl._
    val svc = HttpRoutes.of[F] {
      case req@POST -> Root / "api" / "v2" / "title" / page / rating :? WindowWidthQueryParameterMatcher(ws)
        +& WindowHeightQueryParameterMatcher(wh)
        +& CardWidthQueryParameterMatcher(cs)
        +& CardHeightQueryParameterMatcher(ch) =>
        for {
          reqParams <- req.as[ReqParams]
          rtng <- getRating(rating)
          titleStream <- Sync[F].delay(if (reqParams.year.nonEmpty)
            I.getByTitle(reqParams.query, rtng, reqParams)
          else
            Stream.empty)
          titleList <- titleStream.compile.toList
          dimList <- Sync[F].delay(dimsFromString(List(
            (page, 1),
            (ws, 600),
            (wh, 900),
            (cs, 160),
            (ch, 238),
          )
          ))
          pgs <- Sync[F].delay{
            val wsInt = dimList(1)
            val whInt = dimList(2)
            val csInt = dimList(3)
            val chInt = dimList(4)
            pageSize(wsInt, csInt, whInt, chInt)
          }
          _ <- Sync[F].delay(L.info(s""""params" ws=$ws wh=$wh cs=$cs ch=$ch pgs=$pgs"""))
          portionTitleList <- Sync[F].delay{
            val pg = dimList.head
            titleList.slice((pg - 1) * pgs, (pg - 1) * pgs + pgs)
          }
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
      case req@POST -> Root / "api" / "v2" / "name" / page / rating :? WindowWidthQueryParameterMatcher(ws)
        +& WindowHeightQueryParameterMatcher(wh)
        +& CardWidthQueryParameterMatcher(cs)
        +& CardHeightQueryParameterMatcher(ch) =>
        for {
          reqParams <- req.as[ReqParams]
          rtng <- getRating(rating)
          imdbNameStream <- Sync[F].delay(if (reqParams.year.nonEmpty)
            I.getByEnhancedName(reqParams.query.getOrElse("Joe Blow"), rtng, reqParams)
          else
            Stream.empty)
          nameList <- imdbNameStream.compile.toList
          dimList <- Sync[F].delay(dimsFromString(List(
            (page, 1),
            (ws, 600),
            (wh, 900),
            (cs, 160),
            (ch, 238),
          )
          ))
          pgs <- Sync[F].delay {
            val wsInt = dimList(1)
            val whInt = dimList(2)
            val csInt = dimList(3)
            val chInt = dimList(4)
            pageSize(wsInt, csInt, whInt, chInt)
          }
          _ <- Sync[F].delay(L.info(s""""name params" ws=$ws wh=$wh cs=$cs ch=$ch pgs=$pgs"""))
          portionNameList <- Sync[F].delay{
            val pg = dimList.head
            nameList.slice((pg - 1) * pgs, (pg - 1) * pgs + pgs)
          }
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
