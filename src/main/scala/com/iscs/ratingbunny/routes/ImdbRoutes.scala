package com.iscs.ratingbunny.routes

import cats.effect.*
import cats.effect.implicits.{genSpawnOps, genSpawnOps_}
import cats.implicits.*
import com.iscs.ratingbunny.domains.*
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

object ImdbRoutes extends DecodeUtils with VerifiedUserRoutes:

  private val L          = Logger[this.type]
  private val apiVersion = "v3"

  private object QParamMatcher         extends QueryParamDecoderMatcher[String]("q")
  private object LangParamMatcher      extends OptionalQueryParamDecoderMatcher[String]("lang")
  private object RatingParamMatcher    extends OptionalQueryParamDecoderMatcher[Double]("rating")
  private object VotesParamMatcher     extends OptionalQueryParamDecoderMatcher[Int]("votes")
  private object GenreParamMatcher     extends OptionalMultiQueryParamDecoderMatcher[String]("genre")
  private object TitleTypeParamMatcher extends OptionalMultiQueryParamDecoderMatcher[String]("titletype")
  private object IsAdultParamMatcher   extends OptionalQueryParamDecoderMatcher[Int]("isadult")
  private object LowYearParamMatcher   extends OptionalQueryParamDecoderMatcher[Int]("lowyear")
  private object HighYearParamMatcher  extends OptionalQueryParamDecoderMatcher[Int]("highyear")

  /** Records per search page: a multiple of 2, 3, 4 and 6, the column counts the React grid uses, so every page fills whole rows. */
  private[routes] val PageSize = 24

  /** The slice of a search's full result set that one page covers: `page` is 1-based. */
  final private case class PageWindow(page: Int):
    val offset: Int                  = math.min((page - 1).toLong * PageSize, Int.MaxValue.toLong).toInt
    def remaining(total: Long): Long = math.max(total - page.toLong * PageSize, 0L)

  /** Page numbers that are not numbers, or below 1, are page 1. */
  private def pageWindow(page: String): PageWindow = PageWindow(math.max(page.toIntOption.getOrElse(1), 1))

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
        fetch: (Double, ReqParams, Int, Int, SortField) => Stream[F, TitleRecBase],
        logText: String
    ): F[Response[F]] =
      val win = pageWindow(page)
      for
        params  <- req.as[ReqParams]
        rtg     <- getRating(rating)
        userOpt <- JwtAuth.userFromRequest(req, jwtSecret)
        (pageList, total) <- fetch(rtg, params, win.offset, PageSize, SortField.from(params.sortType)).compile.toList
          .both(I.countByTitle(rtg, params))
        _    <- hx.log(userOpt.getOrElse("guest"), params.copy(rating = Some(rtg))).start
        resp <- Ok(pageList).map(withRemaining(_, win.remaining(total)))
      yield resp

    val svc = HttpRoutes
      .of[F]:
        case req @ POST -> Root / "title" / page / rating =>
          commonTitle[TitleRec](req, page, rating, I.getByTitle, "title")

        case req @ POST -> Root / "pathtitle" / page / rating =>
          commonTitle[TitleRecPath](req, page, rating, I.getByTitlePath, "pathtitle")

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
      authMw: AuthMiddleware[F, String]
  ): HttpRoutes[F] =
    val dsl = Http4sDsl[F]; import dsl.*

    val svc = AuthedRoutes
      .of[String, F]:
        case authreq @ GET -> Root / "autoname"
            :? QParamMatcher(q)
            +& LowYearParamMatcher(lowYear)
            +& HighYearParamMatcher(highYear) as user =>
          ensureVerified(user, userRepo) {
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

        case authreq @ POST -> Root / "name" / page / rating as user =>
          ensureVerified(user, userRepo) {
            val win = pageWindow(page)
            for
              p   <- authreq.req.as[ReqParams]
              _   <- Sync[F].delay(L.info(s"got reqParams: $p"))
              rtg <- getRating(rating)
              name = p.query.getOrElse("")
              (pageLst, total) <- I
                .getByEnhancedName(name, rtg, p, win.offset, PageSize, SortField.from(p.sortType))
                .compile
                .toList
                .both(I.countByName(name, rtg, p))
              _   <- Sync[F].delay(L.info(s"got list: $pageLst"))
              _   <- hx.log(user, p.copy(rating = Some(rtg))).start
              res <- Ok(pageLst).map(withRemaining(_, win.remaining(total)))
            yield res
          }
      .map(
        _.withContentType(`Content-Type`(org.http4s.MediaType.application.json))
      )

    CORSSetup.methodConfig(authMw(svc))
