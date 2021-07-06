package com.iscs.releaseScraper.model

import cats.effect.Sync
import io.circe.Decoder
import io.circe.generic.semiauto._
import org.http4s.EntityDecoder
import org.http4s.circe._

object Requests {
  final case class ReqParams(year: Option[Int], genre: Option[String], titleType: Option[String], isAdult: Option[Boolean]) {
    override def toString: String = List(
      year.map(yr => "year=$yr"),
      genre.map(genre => "genre=$genre"),
      titleType.map(titleType => "titleType=$titleType"),
      isAdult.map(isAdult => s"isAdult=>$isAdult")
    ).flatten
      .mkString(", ")
  }

  implicit val reqParamsDecoder: Decoder[ReqParams] = deriveDecoder[ReqParams]
  implicit def reqParamsEntityDecoder[F[_]: Sync]: EntityDecoder[F, ReqParams] = jsonOf

}
