package com.iscs.ratingbunny.routes

import cats.effect.*
import cats.effect.implicits.{genSpawnOps, genSpawnOps_}
import cats.implicits.*
import com.iscs.ratingbunny.config.TrialConfig
import com.iscs.ratingbunny.domains.*
import com.iscs.ratingbunny.dslparams.*
import com.iscs.ratingbunny.model.Requests.*
import com.iscs.ratingbunny.repos.HistoryRepo
import com.iscs.ratingbunny.util.DecodeUtils
import com.typesafe.scalalogging.Logger
import fs2.Stream
import io.circe.{Encoder, Json}
import io.circe.syntax.EncoderOps
import io.circe.generic.auto.*
import org.http4s.*
import org.http4s.circe.CirceEntityCodec.{circeEntityDecoder, circeEntityEncoder}
import org.http4s.circe.*
import org.http4s.dsl.*
import org.http4s.dsl.impl.{OptionalMultiQueryParamDecoderMatcher, OptionalQueryParamDecoderMatcher, QueryParamDecoderMatcher}
import org.http4s.headers.`Content-Type`
import org.http4s.server.AuthMiddleware
import com.iscs.ratingbunny.routes.VerifiedUserRoutes
import com.iscs.ratingbunny.security.JwtAuth
import org.typelevel.ci.*

import scala.annotation.tailrec
import scala.util.Try

object ImdbRoutes extends DecodeUtils with VerifiedUserRoutes:

  private val L            = Logger[this.type]
  private val rowSpacer    = 64
  private val columnSpacer = 64
  private val apiVersion   = "v3"

  private object QParamMatcher         extends QueryParamDecoderMatcher[String]("q")
  private object LangParamMatcher      extends OptionalQueryParamDecoderMatcher[String]("lang")
  private object RatingParamMatcher    extends OptionalQueryParamDecoderMatcher[Double]("rating")
  private object VotesParamMatcher     extends OptionalQueryParamDecoderMatcher[Int]("votes")
  private object GenreParamMatcher     extends OptionalMultiQueryParamDecoderMatcher[String]("genre")
  private object TitleTypeParamMatcher extends OptionalMultiQueryParamDecoderMatcher[String]("titletype")
  private object IsAdultParamMatcher   extends OptionalQueryParamDecoderMatcher[Int]("isadult")
  private object LowYearParamMatcher   extends OptionalQueryParamDecoderMatcher[Int]("lowyear")
  private object HighYearParamMatcher  extends OptionalQueryParamDecoderMatcher[Int]("highyear")

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
    strPairs.map:
      case (s, d) => Try(s.toInt).toOption.getOrElse(d)

  private def conv(page: String, ws: String, wh: String, cs: String, ch: String, off: String): List[Int] =
    dims(List((page, 1), (ws, 600), (wh, 900), (cs, 160), (ch, 238), (off, 0)))

  /** The slice of a search's full result set that one page covers: `page` is 1-based and `size` is records per page. */
  final private case class PageWindow(page: Int, size: Int):
    val offset: Int                  = math.min((page - 1).toLong * size, Int.MaxValue.toLong).toInt
    def remaining(total: Long): Long = math.max(total - page.toLong * size, 0L)

  private def pageWindow(page: String, ws: String, wh: String, cs: String, ch: String, off: String): PageWindow =
    val dimsL = conv(page, ws, wh, cs, ch, off)
    PageWindow(math.max(dimsL.head, 1), math.max(pageSize(dimsL(1), dimsL(3), dimsL(2), dimsL(4), dimsL(5)), 1))

  private def withRemaining[F[_]](resp: Response[F], remaining: Long): Response[F] =
    resp.putHeaders(
      Header.Raw(ci"X-Remaining-Count", remaining.toString),
      Header.Raw(ci"Access-Control-Expose-Headers", "X-Remaining-Count")
    )

  private def scrubInternal(j: Json): Json =
    j.mapObject(
      _.remove("hasUS")
        .remove("hasEN")
        .remove("langs")
        .remove("langMask")
        .remove("usBoost")
        // add more internal keys here as needed:
        // .remove("someOtherInternalField")
    ).deepDropNullValues

  given encodeAutoRecBase: Encoder[AutoRecBase] = Encoder.instance:
    case autoNameRec: AutoNameRec   => autoNameRec.asJson
    case autoTitleRec: AutoTitleRec => autoTitleRec.asJson

  given encodeTitleRecBase: Encoder[TitleRecBase] = Encoder.instance:
    case titleRec: TitleRec         => scrubInternal(titleRec.asJson)
    case titleRecPath: TitleRecPath => scrubInternal(titleRecPath.asJson)

  // ---------- public TITLE routes -----------------------------------------------
  def publicRoutes[F[_]: Async](I: ImdbQuery[F], hx: HistoryRepo[F], jwtSecret: String): HttpRoutes[F] =
    val dsl = Http4sDsl[F]; import dsl.*

    def commonTitle[T <: TitleRecBase](
        req: Request[F],
        page: String,
        rating: String,
        ws: String,
        wh: String,
        cs: String,
        ch: String,
        off: String,
        fetch: (Double, ReqParams, Int, Int, SortField) => Stream[F, TitleRecBase],
        logText: String
    ): F[Response[F]] =
      val win = pageWindow(page, ws, wh, cs, ch, off)
      for
        params  <- req.as[ReqParams]
        rtg     <- getRating(rating)
        userOpt <- JwtAuth.userFromRequest(req, jwtSecret)
        (pageList, total) <- fetch(rtg, params, win.offset, win.size, SortField.from(params.sortType)).compile.toList
          .both(I.countByTitle(rtg, params))
        _    <- hx.log(userOpt.getOrElse("guest"), params).start
        resp <- Ok(pageList).map(withRemaining(_, win.remaining(total)))
      yield resp

    val svc = HttpRoutes
      .of[F]:
        case req @ POST -> Root / "title" / page / rating
            :? WindowWidthQueryParameterMatcher(ws)
            +& WindowHeightQueryParameterMatcher(wh)
            +& CardWidthQueryParameterMatcher(cs)
            +& CardHeightQueryParameterMatcher(ch)
            +& OffsetQUeryParameterMatcher(off) =>
          commonTitle[TitleRec](req, page, rating, ws, wh, cs, ch, off, I.getByTitle, "title")

        case req @ POST -> Root / "pathtitle" / page / rating
            :? WindowWidthQueryParameterMatcher(ws)
            +& WindowHeightQueryParameterMatcher(wh)
            +& CardWidthQueryParameterMatcher(cs)
            +& CardHeightQueryParameterMatcher(ch)
            +& OffsetQUeryParameterMatcher(off) =>
          commonTitle[TitleRecPath](req, page, rating, ws, wh, cs, ch, off, I.getByTitlePath, "pathtitle")

        case req @ GET -> Root / "autotitle"
            :? QParamMatcher(title)
            +& LangParamMatcher(lang)
            +& TitleTypeParamMatcher(titleTypes)
            +& IsAdultParamMatcher(isAdult) =>
          val params = ReqParams(
            titleType = titleTypes.toOption.map(_.flatMap(_.split(","))).filter(_.nonEmpty),
            isAdult = isAdult.map(_ != 0)
          )

          for
            userOpt <- JwtAuth.userFromRequest(req, jwtSecret)
            lst     <- I.getAutosuggestTitle(title, lang, params).compile.toList
            res     <- Ok(lst)
          yield res
      .map(
        _.withContentType(`Content-Type`(org.http4s.MediaType.application.json))
      )

    CORSSetup.methodConfig(svc)

  // ---------- AUTHED NAME routes --------------------------------------------------
  def authedRoutes[F[_]: Async](
      I: ImdbQuery[F],
      hx: HistoryRepo[F],
      userRepo: UserRepo[F],
      authMw: AuthMiddleware[F, String],
      trialConfig: TrialConfig
  ): HttpRoutes[F] =
    val dsl = Http4sDsl[F]; import dsl.*

    val svc = AuthedRoutes
      .of[String, F]:
        case authreq @ GET -> Root / "autoname"
            :? QParamMatcher(q)
            +& LowYearParamMatcher(lowYear)
            +& HighYearParamMatcher(highYear) as user =>
          ensureVerified(user, trialConfig, userRepo) {
            val low  = lowYear.getOrElse(Int.MinValue)
            val high = highYear.getOrElse(Int.MaxValue)
            val params = ReqParams(
              query = Some(q),
              year = Some(List(low, high))
            )

            for
              lst <- I.getAutosuggestName(q, low, high).compile.toList
              res <- Ok(lst)
            yield res
          }

        case authreq @ POST -> Root / "name" / page / rating
            :? WindowWidthQueryParameterMatcher(ws)
            +& WindowHeightQueryParameterMatcher(wh)
            +& CardWidthQueryParameterMatcher(cs)
            +& CardHeightQueryParameterMatcher(ch)
            +& OffsetQUeryParameterMatcher(off) as user =>
          ensureVerified(user, trialConfig, userRepo) {
            val win = pageWindow(page, ws, wh, cs, ch, off)
            for
              p   <- authreq.req.as[ReqParams]
              _   <- Sync[F].delay(L.info(s"got reqParams: $p"))
              rtg <- getRating(rating)
              name = p.query.getOrElse("")
              (pageLst, total) <- I
                .getByEnhancedName(name, rtg, p, win.offset, win.size, SortField.from(p.sortType))
                .compile
                .toList
                .both(I.countByName(name, rtg, p))
              _   <- Sync[F].delay(L.info(s"got list: $pageLst"))
              _   <- hx.log(user, p).start
              res <- Ok(pageLst).map(withRemaining(_, win.remaining(total)))
            yield res
          }
      .map(
        _.withContentType(`Content-Type`(org.http4s.MediaType.application.json))
      )

    CORSSetup.methodConfig(authMw(svc))
