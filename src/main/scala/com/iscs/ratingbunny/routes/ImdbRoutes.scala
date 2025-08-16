package com.iscs.ratingbunny.routes

import cats.effect.*
import cats.effect.implicits.genSpawnOps
import cats.implicits.*
import com.iscs.ratingbunny.domains.*
import com.iscs.ratingbunny.dslparams.*
import com.iscs.ratingbunny.model.Requests.*
import com.iscs.ratingbunny.repos.HistoryRepo
import com.iscs.ratingbunny.util.DecodeUtils
import com.typesafe.scalalogging.Logger
import fs2.Stream
import io.circe.Encoder
import io.circe.syntax.EncoderOps
import io.circe.generic.auto.*
import org.http4s.*
import org.http4s.circe.CirceEntityCodec.{circeEntityDecoder, circeEntityEncoder}
import org.http4s.circe.*
import org.http4s.dsl.*
import org.http4s.headers.`Content-Type`
import org.http4s.server.AuthMiddleware
import com.iscs.ratingbunny.security.JwtAuth
import org.typelevel.ci.*

import scala.annotation.tailrec
import scala.util.Try

object ImdbRoutes extends DecodeUtils:

  private val L            = Logger[this.type]
  private val rowSpacer    = 64
  private val columnSpacer = 64
  private val apiVersion   = "v3"

  // ---------- helpers (unchanged) ------------------------------------------------
  private def rowsPerPage(height: Int, spacer: Int, cardHeight: Int): Int =
    @tailrec
    def rowsPerPageTR(rem: Int, c: Int): Int =
      if rem < 0 then c else rowsPerPageTR(rem - spacer - cardHeight, c + 1)
    rowsPerPageTR(height, 0)

  private def cardsPerRow(width: Int, spacer: Int, offset: Int, cardWidth: Int): Int =
    @tailrec
    def cardsPerRowTR(rem: Int, c: Int): Int =
      if rem < 0 then c else cardsPerRowTR(rem - spacer - cardWidth, c + 1)
    cardsPerRowTR(width - offset, 0)

  private def pageSize(w: Int, cw: Int, h: Int, ch: Int, off: Int): Int =
    cardsPerRow(w, columnSpacer, off, cw) * rowsPerPage(h, rowSpacer, ch)

  private def dims(strPairs: List[(String, Int)]): List[Int] =
    strPairs.map { case (s, d) => Try(s.toInt).toOption.getOrElse(d) }

  private def conv(page: String, ws: String, wh: String, cs: String, ch: String, off: String): List[Int] =
    dims(List((page, 1), (ws, 600), (wh, 900), (cs, 160), (ch, 238), (off, 0)))

  private def pureExtract[T](recs: List[T], pg: Int, pgs: Int): List[T] =
    recs.slice((pg - 1) * pgs, (pg - 1) * pgs + pgs)

  private def remain(pg: Int, pgs: Int, total: Int): Int =
    math.max(total - (pg * pgs), 0)

  implicit val encodeAutoRecBase: Encoder[AutoRecBase] = Encoder.instance {
    case autoNameRec: AutoNameRec   => autoNameRec.asJson
    case autoTitleRec: AutoTitleRec => autoTitleRec.asJson
  }

  implicit val encodeTitleRecBase: Encoder[TitleRecBase] = Encoder.instance {
    case titleRec: TitleRec         => titleRec.asJson
    case titleRecPath: TitleRecPath => titleRecPath.asJson
  }

  // ---------- public TITLE routes -----------------------------------------------
  def publicRoutes[F[_]: Async](I: ImdbQuery[F], hx: HistoryRepo[F], jwtSecret: String): HttpRoutes[F] =
    val dsl = Http4sDsl[F]; import dsl.*

    def commonTitle(
        req: Request[F],
        page: String,
        rating: String,
        ws: String,
        wh: String,
        cs: String,
        ch: String,
        off: String,
        fetch: (Double, ReqParams, Int, SortField) => Stream[F, TitleRecBase],
        logText: String
    ): F[Response[F]] =
      for
        params  <- req.as[ReqParams]
        rtg     <- getRating(rating)
        userOpt <- JwtAuth.userFromRequest(req, jwtSecret)
        dimsL   = conv(page, ws, wh, cs, ch, off)
        pgs     = pageSize(dimsL(1), dimsL(3), dimsL(2), dimsL(4), dimsL(5))
        results <- Sync[F]
          .delay(
            if params.year.nonEmpty then fetch(rtg, params, pgs << 3, SortField.from(params.sortType))
            else Stream.empty
          )
          .flatMap(_.compile.toList)
        pageList = pureExtract(results, dimsL.head, pgs)
        left     = remain(dimsL.head, pgs, results.size)
        _ <- hx.log(userOpt.getOrElse("guest"), params).start
        resp <- Ok(pageList).map(
          _.putHeaders(
            Header.Raw(ci"X-Remaining-Count", left.toString),
            Header.Raw(ci"Access-Control-Expose-Headers", "X-Remaining-Count")
          )
        )
      yield resp

    val svc = HttpRoutes
      .of[F] {
        case req @ POST -> Root / "title" / page / rating
            :? WindowWidthQueryParameterMatcher(ws)
            +& WindowHeightQueryParameterMatcher(wh)
            +& CardWidthQueryParameterMatcher(cs)
            +& CardHeightQueryParameterMatcher(ch)
            +& OffsetQUeryParameterMatcher(off) =>
          commonTitle(req, page, rating, ws, wh, cs, ch, off, I.getByTitle, "title")

        case req @ POST -> Root / "pathtitle" / page / rating
            :? WindowWidthQueryParameterMatcher(ws)
            +& WindowHeightQueryParameterMatcher(wh)
            +& CardWidthQueryParameterMatcher(cs)
            +& CardHeightQueryParameterMatcher(ch)
            +& OffsetQUeryParameterMatcher(off) =>
          commonTitle(req, page, rating, ws, wh, cs, ch, off, I.getByTitlePath, "pathtitle")

        case req @ POST -> Root / "autotitle" / title / rating =>
          for
            p       <- req.as[ReqParams]
            rtg     <- getRating(rating)
            userOpt <- JwtAuth.userFromRequest(req, jwtSecret)
            lst     <- I.getAutosuggestTitle(title, rtg, p).compile.toList
            _       <- hx.log(userOpt.getOrElse("guest"), p).start
            res     <- Ok(lst)
          yield res
      }
      .map(
        _.withContentType(`Content-Type`(org.http4s.MediaType.application.json))
      )

    CORSSetup.methodConfig(svc)

  // ---------- AUTHED NAME routes --------------------------------------------------
  def authedRoutes[F[_]: Async](I: ImdbQuery[F], hx: HistoryRepo[F], authMw: AuthMiddleware[F, String]): HttpRoutes[F] =
    val dsl = Http4sDsl[F]; import dsl.*

    val svc = AuthedRoutes
      .of[String, F] {
        case authreq @ POST -> Root / "name2" / name / rating as user =>
          for
            p   <- authreq.req.as[ReqParams]
            rtg <- getRating(rating)
            lst <- I.getByName(name, rtg, p, SortField.from(p.sortType)).compile.toList
            _   <- hx.log(user, p).start
            res <- Ok(lst)
          yield res

        case authreq @ POST -> Root / "name" / page / rating
            :? WindowWidthQueryParameterMatcher(ws)
            +& WindowHeightQueryParameterMatcher(wh)
            +& CardWidthQueryParameterMatcher(cs)
            +& CardHeightQueryParameterMatcher(ch)
            +& OffsetQUeryParameterMatcher(off) as user =>
          val dimsL = conv(page, ws, wh, cs, ch, off)
          val pgs   = pageSize(dimsL(1), dimsL(3), dimsL(2), dimsL(4), dimsL(5))
          for
            p   <- authreq.req.as[ReqParams]
            _   <- Sync[F].delay(L.info(s"got reqParams: $p"))
            rtg <- getRating(rating)
            lst <- I
              .getByEnhancedName(p.query.getOrElse(""), rtg, p, pgs << 3, SortField.from(p.sortType))
              .compile
              .toList
            _        <- Sync[F].delay(L.info(s"got list: $lst"))
            pageLst   = pureExtract(lst, dimsL.head, pgs)
            left      = remain(dimsL.head, pgs, lst.size)
            _ <- hx.log(user, p).start
            res <- Ok(pageLst).map(
              _.putHeaders(
                Header.Raw(ci"X-Remaining-Count", left.toString),
                Header.Raw(ci"Access-Control-Expose-Headers", "X-Remaining-Count")
              )
            )
          yield res

        case authreq @ POST -> Root / "autoname" / name / rating as user =>
          for
            p   <- authreq.req.as[ReqParams]
            rtg <- getRating(rating)
            lst <- I.getAutosuggestName(name, rtg, p).compile.toList
            _   <- hx.log(user, p).start
            res <- Ok(lst)
          yield res
      }
      .map(
        _.withContentType(`Content-Type`(org.http4s.MediaType.application.json))
      )

    CORSSetup.methodConfig(authMw(svc))
