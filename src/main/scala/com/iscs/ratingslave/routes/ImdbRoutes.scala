package com.iscs.ratingslave.routes

import cats.effect.*
import cats.implicits.*
import com.iscs.ratingslave.domains.{AutoNameRec, AutoRecBase, AutoTitleRec, ImdbQuery, TitleRec, TitleRecBase, TitleRecPath}
import com.iscs.ratingslave.dslparams.*
import com.iscs.ratingslave.model.Requests.*
import com.iscs.ratingslave.util.DecodeUtils
import com.typesafe.scalalogging.Logger
import fs2.Stream
import io.circe.Encoder
import io.circe.syntax.EncoderOps
import io.circe.generic.auto.*
import org.http4s.{EntityEncoder, Header, HttpRoutes, Request, Response}
import org.http4s.MediaType.application.*
import org.http4s.circe.CirceEntityCodec.{circeEntityDecoder, circeEntityEncoder}
import org.http4s.circe.jsonEncoderOf
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`Content-Type`
import org.typelevel.ci.*

import scala.annotation.tailrec
import scala.util.Try

object ImdbRoutes extends DecodeUtils {
  private val L            = Logger[this.type]
  private val rowSpacer    = 64
  private val columnSpacer = 64
  private val apiVersion   = "v3"

  private def rowsPerPage(height: Int, spacer: Int, cardHeight: Int): Int = {
    @tailrec
    def rowsPerPageTR(remainingHeight: Int, count: Int): Int =
      if (remainingHeight < 0) count
      else rowsPerPageTR(remainingHeight - spacer - cardHeight, count + 1)
    rowsPerPageTR(height, 0)
  }
  private def cardsPerRow(width: Int, spacer: Int, offset: Int, cardWidth: Int): Int = {
    @tailrec
    def cardsPerRowTR(remainingWidth: Int, count: Int): Int =
      if (remainingWidth < 0) count
      else cardsPerRowTR(remainingWidth - spacer - cardWidth, count + 1)
    cardsPerRowTR(width - offset, 0)
  }

  private def pageSize(width: Int, cardWidth: Int, height: Int, cardHeight: Int, offset: Int): Int =
    cardsPerRow(width, columnSpacer, offset, cardWidth) * rowsPerPage(height, rowSpacer, cardHeight)

  private def dimsFromString(strPairs: List[(String, Int)]): List[Int] =
    strPairs.map { case (strVal, defInt) =>
      Try(strVal.toInt).toOption.getOrElse(defInt)
    }

  private def convertParams(page: String, ws: String, wh: String, cs: String, ch: String, offset: String): List[Int] =
    dimsFromString(
      List(
        (page, 1),
        (ws, 600),
        (wh, 900),
        (cs, 160),
        (ch, 238),
        (offset, 0)
      )
    )

  private def calcWithParams(params: List[Int]): Int = {
    val wsInt     = params(1)
    val whInt     = params(2)
    val csInt     = params(3)
    val chInt     = params(4)
    val offsetInt = params(5)
    pageSize(wsInt, csInt, whInt, chInt, offsetInt)
  }

  private def pureExtractRecords[T](records: List[T], pg: Int, pgs: Int): List[T] =
    records.slice((pg - 1) * pgs, (pg - 1) * pgs + pgs)

  private def showParams[F[_]: Sync](paramType: String, pgs: Int, ws: String, wh: String, cs: String, ch: String, offset: String): F[Unit] =
    Sync[F].delay(L.info(s""""$paramType params" ws=$ws wh=$wh cs=$cs ch=$ch pgs=$pgs offset=$offset"""))

  private def remainingCount(currentPage: Int, pageSize: Int, totalSize: Int): Int =
    math.max(totalSize - (currentPage * pageSize), 0)

  implicit val encodeAutoRecBase: Encoder[AutoRecBase] = Encoder.instance {
    case autoNameRec: AutoNameRec   => autoNameRec.asJson
    case autoTitleRec: AutoTitleRec => autoTitleRec.asJson
  }

  implicit val encodeTitleRecBase: Encoder[TitleRecBase] = Encoder.instance {
    case titleRec: TitleRec         => titleRec.asJson
    case titleRecPath: TitleRecPath => titleRecPath.asJson
  }

  implicit def listTitleRecBaseEntityEncoder[F[_]: Async]: EntityEncoder[F, List[TitleRecBase]] =
    jsonEncoderOf[F, List[TitleRecBase]]

  def httpRoutes[F[_]: Async](I: ImdbQuery[F]): HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl._

    def handleTitleRequest(
        req: Request[F],
        page: String,
        rating: String,
        ws: String,
        wh: String,
        cs: String,
        ch: String,
        offset: String,
        getTitle: (Option[String], Double, ReqParams, Int) => Stream[F, TitleRecBase],
        logText: String
    ): F[Response[F]] =
      for {
        reqParams <- req.as[ReqParams]
        rtng      <- getRating(rating)
        dimList = convertParams(page, ws, wh, cs, ch, offset)
        pgs     = calcWithParams(dimList)
        _ <- showParams("title", pgs, ws, wh, cs, ch, offset)
        titleStream <- Sync[F].delay(
          if (reqParams.year.nonEmpty)
            getTitle(reqParams.query, rtng, reqParams, pgs << 3)
          else
            Stream.empty
        )
        titleList <- titleStream.compile.toList
        pageList = pureExtractRecords(titleList, dimList.head, pgs)
        _ <- Sync[F].delay(L.info(s""""$logText counts" pageNo=$page titleList=${titleList.size} pageList=${pageList.size}"""))
        elementsLeft = remainingCount(dimList.head, pgs, titleList.size)
        resp <- Ok(pageList).map(
          _.putHeaders(
            Header.Raw(ci"X-Remaining-Count", elementsLeft.toString),
            Header.Raw(ci"Access-Control-Expose-Headers", "X-Remaining-Count")
          )
        )
      } yield resp

    def handleAutoRequest(
        req: Request[F],
        name: String,
        rating: String,
        getAutoRec: (String, Double, ReqParams) => Stream[F, AutoRecBase],
        logType: String
    ): F[Response[F]] =
      for {
        _         <- Sync[F].delay(L.info(s""""request" $logType=$name rating=$rating"""))
        reqParams <- req.as[ReqParams]
        rtng      <- getRating(rating)
        stream    <- Sync[F].delay(getAutoRec(name, rtng, reqParams))
        strList   <- stream.compile.toList
        resp      <- Ok(strList)
      } yield resp

    val svc = HttpRoutes
      .of[F] {
        case req @ POST -> Root / "api" / `apiVersion` / "title" / page / rating :? WindowWidthQueryParameterMatcher(ws)
            +& WindowHeightQueryParameterMatcher(wh) +& CardWidthQueryParameterMatcher(cs)
            +& CardHeightQueryParameterMatcher(ch) +& OffsetQUeryParameterMatcher(offset) =>
          handleTitleRequest(req, page, rating, ws, wh, cs, ch, offset, I.getByTitle, "title")
        case req @ POST -> Root / "api" / `apiVersion` / "pathtitle" / page / rating :? WindowWidthQueryParameterMatcher(ws)
            +& WindowHeightQueryParameterMatcher(wh) +& CardWidthQueryParameterMatcher(cs)
            +& CardHeightQueryParameterMatcher(ch) +& OffsetQUeryParameterMatcher(offset) =>
          handleTitleRequest(req, page, rating, ws, wh, cs, ch, offset, I.getByTitlePath, "titlepath")
        case req @ POST -> Root / "api" / `apiVersion` / "name2" / name / rating =>
          for {
            reqParams <- req.as[ReqParams]
            rtng      <- getRating(rating)
            imdbNameStream <- Sync[F].delay(
              if (reqParams.year.nonEmpty)
                I.getByName(name, rtng, reqParams)
              else
                Stream.empty
            )
            nameList <- imdbNameStream.compile.toList
            resp     <- Ok(nameList)
          } yield resp
        case req @ POST -> Root / "api" / `apiVersion` / "name" / page / rating :? WindowWidthQueryParameterMatcher(ws)
            +& WindowHeightQueryParameterMatcher(wh) +& CardWidthQueryParameterMatcher(cs)
            +& CardHeightQueryParameterMatcher(ch) +& OffsetQUeryParameterMatcher(offset) =>
          for {
            reqParams <- req.as[ReqParams]
            rtng      <- getRating(rating)
            dimList = convertParams(page, ws, wh, cs, ch, offset)
            pgs     = calcWithParams(dimList)
            _ <- showParams("name", pgs, ws, wh, cs, ch, offset)
            imdbNameStream <- Sync[F].delay(
              if (reqParams.year.nonEmpty)
                I.getByEnhancedName(reqParams.query.getOrElse("Joe Blow"), rtng, reqParams, pgs << 3)
              else
                Stream.empty
            )
            nameList <- imdbNameStream.compile.toList
            pageList = pureExtractRecords(nameList, dimList.head, pgs)
            _ <- Sync[F].delay(L.info(s""""name counts" page=$page nameList=${nameList.size} pageList=${pageList.size}"""))
            elementsLeft = remainingCount(dimList.head, pgs, nameList.size)
            resp <- Ok(pageList).map(
              _.putHeaders(
                Header.Raw(ci"X-Remaining-Count", elementsLeft.toString),
                Header.Raw(ci"Access-Control-Expose-Headers", "X-Remaining-Count")
              )
            )
          } yield resp
        case req @ POST -> Root / "api" / `apiVersion` / "autoname" / name / rating =>
          handleAutoRequest(req, name, rating, I.getAutosuggestName, "autoname")
        case req @ POST -> Root / "api" / `apiVersion` / "autotitle" / title / rating =>
          handleAutoRequest(req, title, rating, I.getAutosuggestTitle, "autotitle")
      }
      .map(_.withContentType(`Content-Type`(`json`)))
    CORSSetup.methodConfig(svc)
  }
}
