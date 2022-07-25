package com.iscs.ratingslave.model

import cats.Applicative
import zio.json.{DeriveJsonDecoder, JsonDecoder}
import zio.json.interop.http4s._
import cats.effect.{Concurrent, Sync}
/*import io.circe.Decoder
import io.circe.generic.semiauto._ */
import org.http4s.EntityDecoder
/* import org.http4s.circe._*/

object Requests {
  final case class ReqParams(query: Option[String],
                             votes: Option[Int],
                             year: Option[List[Int]],
                             genre: Option[List[String]],
                             titleType: Option[List[String]],
                             isAdult: Option[Boolean]) {
    override def toString: String = List(
      query.map(q => s"query=$q"),
      votes.map(v => s"votes=$v"),
      year.map(yr => s"year=${yr.head} to ${yr.last}"),
      genre.map(genre => s"genre=${genre.mkString(",")}"),
      titleType.map(titleType => s"titleType=${titleType.mkString(",")}"),
      isAdult.map(isAdult => s"isAdult=>$isAdult")
    ).flatten
      .mkString(", ")
  }

  implicit val reqParamsDecoder: JsonDecoder[ReqParams] = DeriveJsonDecoder.gen[ReqParams]
  // implicit val reqParamsDecoder: Decoder[ReqParams] = deriveDecoder[ReqParams]
  implicit def reqParamsEntityDecoder[F[_]: Applicative: Concurrent]: EntityDecoder[F, ReqParams] = jsonOf

}
