package com.iscs.ratingslave.routes

import cats.effect.Sync
import com.iscs.ratingslave.domains.ImdbQuery
import com.iscs.ratingslave.model.Requests._
import com.iscs.ratingslave.util.DecodeUtils.getRating
import com.typesafe.scalalogging.Logger
import fs2.Stream
import org.http4s.MediaType.application._
import org.http4s.dsl.Http4sDsl
import org.http4s.HttpRoutes
import org.http4s.headers.`Content-Type`
import zio.json._

object ImdbRoutess {
  private val L = Logger[this.type]

  private val dummyTitle = ImdbQuery.TitleRec(None, None, "", "", "", 0, 0, "", None, List())

  def httpRoutes[F[_]: Sync](I: ImdbQuery[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._
    HttpRoutes.of[F] {
      case req@POST -> Root / "api" / "v1" / "title" / rating =>
        Ok(for {
          asBytes <- Stream.eval(req.body.compile.toList)
          reqqParams <- Stream.eval(Sync[F].delay(asBytes.map(_.toChar).mkString.fromJson[ReqParams].getOrElse(ReqParams(year = Some(List())))))
          rtng <- Stream.eval(getRating(rating))
          resp <- reqqParams match {
            case ReqParams(query, _, Some(year), _, _, _) if year.nonEmpty => I.getByTitle(query, rtng, reqqParams)
            case _                                                         => Stream.eval(Sync[F].delay(dummyTitle))
          }
          scrapeJson <- Stream.eval(Sync[F].delay(resp.toJson))
        } yield scrapeJson)
      case req@POST -> Root / "api" / "v1" / "name2" / name / rating =>
        Ok(for {
          asBytes <- Stream.eval(req.body.compile.toList)
          reqqParams <- Stream.eval(Sync[F].delay(asBytes.map(_.toChar).mkString.fromJson[ReqParams].getOrElse(ReqParams(year = Some(List())))))
          rtng <- Stream.eval(getRating(rating))
          resp <- reqqParams match {
            case ReqParams(_, _, Some(year), _, _, _) if year.nonEmpty => I.getByName(name, rtng, reqqParams)
            case _                                                     => Stream.eval(Sync[F].delay(dummyTitle))
          }
          scrapeJson <- Stream.eval(Sync[F].delay(resp.toJson))
        } yield scrapeJson)
      case req@POST -> Root / "api" / "v1" / "name" / name / rating =>
        Ok(for {
          asBytes <- Stream.eval(req.body.compile.toList)
          reqqParams <- Stream.eval(Sync[F].delay(asBytes.map(_.toChar).mkString.fromJson[ReqParams].getOrElse(ReqParams(year = Some(List())))))
          rtng <- Stream.eval(getRating(rating))
          resp <- reqqParams match {
            case ReqParams(_, _, Some(year), _, _, _) if year.nonEmpty => I.getByEnhancedName(name, rtng, reqqParams)
            case _                                                     => Stream.eval(Sync[F].delay(dummyTitle))
          }
          scrapeJson <- Stream.eval(Sync[F].delay(resp.toJson))
        } yield scrapeJson)
      case GET -> Root / "api" / "v1" / "autoname" / name =>
        Ok(for {
          _ <- Stream.eval(Sync[F].delay(L.info(s""""request" autoname=$name""")))
          resp <- I.getAutosuggestName(name)
          scrapeJson <- Stream.eval(Sync[F].delay(resp.toJson))
        } yield scrapeJson)
      case GET -> Root / "api" / "v1" / "autotitle" / title =>
        Ok(for {
          _ <- Stream.eval(Sync[F].delay(L.info(s""""request" autotitle=$title""")))
          resp <- I.getAutosuggestTitle(title)
          scrapeJson <- Stream.eval(Sync[F].delay(resp.toJson))
        } yield scrapeJson)
    }.map(_.withContentType(`Content-Type`(`json`)))
  }
}
