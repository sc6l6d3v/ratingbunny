package com.iscs.ratingbunny.routes

import cats.effect.*
import cats.implicits.*
import com.iscs.ratingbunny.domains.{TitleRange, TitleRanges}
import io.circe.{Encoder, Json}
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityCodec.circeEntityEncoder
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.impl.OptionalQueryParamDecoderMatcher

object TitleRangesRoutes:

  private object TypeParamMatcher extends OptionalQueryParamDecoderMatcher[String]("type")

  private val fieldGroups: Map[String, Set[String]] = Map(
    "runtimeMinutes" -> Set("minRuntimeMinutes", "maxRuntimeMinutes"),
    "startYear"      -> Set("minStartYear", "maxStartYear"),
    "ratingAverage"  -> Set("minRatingAverage", "maxRatingAverage"),
    "ratingVotes"    -> Set("minRatingVotes", "maxRatingVotes"),
    "usBoost"        -> Set("minUsBoost", "maxUsBoost")
  )

  private def filterFields(doc: Json, typeParam: String): Json =
    val keep = typeParam.split(",").flatMap(t => fieldGroups.get(t.trim)).flatten.toSet + "_id"
    doc.mapObject(obj => obj.filterKeys(keep.contains))

  def httpRoutes[F[_]: Async](R: TitleRanges[F]): HttpRoutes[F] =
    val dsl = Http4sDsl[F]
    import dsl.*
    val svc = HttpRoutes.of[F]:
      case GET -> Root / "ranges" :? TypeParamMatcher(typeOpt) =>
        for
          all  <- R.getAll
          json = all.map(_.asJson)
          filtered = typeOpt.fold(json)(t => json.map(filterFields(_, t)))
          resp <- Ok(filtered)
        yield resp
    CORSSetup.methodConfig(svc)
