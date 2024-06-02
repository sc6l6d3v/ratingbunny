package com.iscs.ratingslave.routes

import cats.effect._
import cats.implicits._
import com.iscs.ratingslave.domains.ImdbQuery
import com.iscs.ratingslave.dslparams._
import com.iscs.ratingslave.model.Requests._
import com.iscs.ratingslave.util.DecodeUtils
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

object ImdbRoutes extends DecodeUtils {
  private val L = Logger[this.type]
  private val rowSpacer = 64
  private val columnSpacer = 64
  private val apiVersion = "v3"

  private def rowsPerPage(height: Int, spacer: Int, cardHeight: Int): Int = {
    @tailrec
    def rowsPerPageTR(remainingHeight: Int, count: Int): Int = {
      if (remainingHeight < 0) count
      else rowsPerPageTR(remainingHeight - spacer - cardHeight, count + 1)
    }
    rowsPerPageTR(height, 0)
  }
  private def cardsPerRow(width: Int, spacer: Int, offset: Int, cardWidth: Int): Int = {
    @tailrec
    def cardsPerRowTR(remainingWidth: Int, count: Int): Int = {
      if (remainingWidth < 0) count
      else cardsPerRowTR(remainingWidth - spacer - cardWidth, count + 1)
    }
    cardsPerRowTR(width - offset, 0)
  }

  private def pageSize(width: Int, cardWidth: Int, height: Int, cardHeight: Int, offset: Int): Int =
    cardsPerRow(width, columnSpacer, offset, cardWidth) * rowsPerPage(height, rowSpacer, cardHeight)

  private def dimsFromString(strPairs: List[(String, Int)]): List[Int] = {
    strPairs.map { case (strVal, defInt) =>
      Try(strVal.toInt).toOption.getOrElse(defInt)
    }
  }

  private def convertParams(page: String, ws: String, wh: String, cs: String,
                                        ch: String, offset: String): List[Int] =
    dimsFromString(
      List(
        (page, 1),
        (ws, 600),
        (wh, 900),
        (cs, 160),
        (ch, 238),
        (offset, 0),
      )
    )

  private def calcWithParams(params: List[Int]): Int = {
      val wsInt = params(1)
      val whInt = params(2)
      val csInt = params(3)
      val chInt = params(4)
      val offsetInt = params(5)
      pageSize(wsInt, csInt, whInt, chInt, offsetInt)
    }

  private def pureExtractRecords[T](records: List[T], pg: Int, pgs: Int): List[T] =
    records.slice((pg - 1) * pgs, (pg - 1) * pgs + pgs)

  private def showParams[F[_]: Sync](pgs: Int, ws: String, wh: String, cs: String, ch: String, offset: String): F[Unit] =
    Sync[F].delay(L.info(s""""params" ws=$ws wh=$wh cs=$cs ch=$ch pgs=$pgs offset=$offset"""))

  def httpRoutes[F[_]: Async](I: ImdbQuery[F]): HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl._
    val svc = HttpRoutes.of[F] {
      case req@POST -> Root / "api" / `apiVersion` / "title" / page / rating :? WindowWidthQueryParameterMatcher(ws)
        +& WindowHeightQueryParameterMatcher(wh) +& CardWidthQueryParameterMatcher(cs)
        +& CardHeightQueryParameterMatcher(ch)   +& OffsetQUeryParameterMatcher(offset) =>
        for {
          reqParams <- req.as[ReqParams]
          rtng <- getRating(rating)
          dimList = convertParams(page, ws, wh, cs, ch, offset)
          pgs = calcWithParams(dimList)
          _ <- showParams(pgs, ws, wh, cs, ch, offset)
          titleStream <- Sync[F].delay(if (reqParams.year.nonEmpty)
            I.getByTitle(reqParams.query, rtng, reqParams, pgs << 3)
          else
            Stream.empty)
          titleList <- titleStream.compile.toList
          pageList = pureExtractRecords(titleList, dimList.head, pgs)
          _ <- Sync[F].delay(L.info(s""""title counts" page=$page titleList=${titleList.size} pageList=${pageList.size}"""))
          resp <- Ok(pageList)
        } yield resp
      case req@POST -> Root / "api" / `apiVersion` / "pathtitle" / page / rating :? WindowWidthQueryParameterMatcher(ws)
        +& WindowHeightQueryParameterMatcher(wh) +& CardWidthQueryParameterMatcher(cs)
        +& CardHeightQueryParameterMatcher(ch) +& OffsetQUeryParameterMatcher(offset) =>
        for {
          reqParams <- req.as[ReqParams]
          rtng <- getRating(rating)
          dimList = convertParams(page, ws, wh, cs, ch, offset)
          pgs = calcWithParams(dimList)
          _ <- showParams(pgs, ws, wh, cs, ch, offset)
          titleStream <- Sync[F].delay(if (reqParams.year.nonEmpty)
            I.getByTitlePath(reqParams.query, rtng, reqParams)
          else
            Stream.empty)
          titleList <- titleStream.compile.toList
          pageList = pureExtractRecords(titleList, dimList.head, pgs)
          _ <- Sync[F].delay(L.info(s""""pathtitle counts" page=$page titleList=${titleList.size} pageList=${pageList.size}"""))
          resp <- Ok(pageList)
        } yield resp
      case req@POST -> Root / "api" / `apiVersion` / "name2" / name / rating =>
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
      case req@POST -> Root / "api" / `apiVersion` / "name" / page / rating :? WindowWidthQueryParameterMatcher(ws)
        +& WindowHeightQueryParameterMatcher(wh) +& CardWidthQueryParameterMatcher(cs)
        +& CardHeightQueryParameterMatcher(ch)   +& OffsetQUeryParameterMatcher(offset) =>
        for {
          reqParams <- req.as[ReqParams]
          rtng <- getRating(rating)
          imdbNameStream <- Sync[F].delay(if (reqParams.year.nonEmpty)
            I.getByEnhancedName(reqParams.query.getOrElse("Joe Blow"), rtng, reqParams)
          else
            Stream.empty)
          nameList <- imdbNameStream.compile.toList
          dimList = convertParams(page, ws, wh, cs, ch, offset)
          pgs = calcWithParams(dimList)
           _ <- Sync[F].delay(L.info(s""""name params" ws=$ws wh=$wh cs=$cs ch=$ch pgs=$pgs offset=$offset"""))
          pageList = pureExtractRecords(nameList, dimList.head, pgs)
          _ <- Sync[F].delay(L.info(s""""name counts" page=$page nameList=${nameList.size} wh=$wh pageList=${pageList.size}"""))
          resp <- Ok(pageList)
        } yield resp
      case req@POST -> Root / "api" / `apiVersion` / "autoname" / name / rating =>
        for {
          _ <- Sync[F].delay(L.info(s""""request" autoname=$name rating=$rating"""))
          reqParams <- req.as[ReqParams]
          rtng <- getRating(rating)
          nameStream <- Sync[F].delay(I.getAutosuggestName(name, rtng, reqParams))
          nameList <- nameStream.compile.toList
          resp <- Ok(nameList)
        } yield resp
      case req@POST -> Root / "api" / `apiVersion` / "autotitle" / title / rating =>
        for {
          _ <- Sync[F].delay(L.info(s""""request" autotitle=$title rating=$rating"""))
          reqParams <- req.as[ReqParams]
          rtng <- getRating(rating)
          titleStream <- Sync[F].delay(I.getAutosuggestTitle(title, rtng, reqParams))
          titleList <- titleStream.compile.toList
          resp <- Ok(titleList)
        } yield resp
    }.map(_.withContentType(`Content-Type`(`json`)))
    CORSSetup.methodConfig(svc)
  }
}
