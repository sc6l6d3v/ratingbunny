package com.iscs.ratingslave.model

import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

object Requests {
  final case class ReqParams(query: Option[String] = None,
                             votes: Option[Int] = None,
                             year: Option[List[Int]] = None,
                             genre: Option[List[String]] = None,
                             titleType: Option[List[String]] = None,
                             isAdult: Option[Boolean] = None) {
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
  implicit val reqParamsEncoder: JsonEncoder[ReqParams] = DeriveJsonEncoder.gen[ReqParams]
}
