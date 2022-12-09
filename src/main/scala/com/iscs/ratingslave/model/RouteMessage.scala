package com.iscs.ratingslave.model

import zio.json._

object RouteMessage {
  final case class RouteMessage(message: String)

  object RouteMessage {
    implicit val scrapeDecoder: JsonDecoder[RouteMessage] = DeriveJsonDecoder.gen[RouteMessage]
    implicit def scrapeEncoder: JsonEncoder[RouteMessage] = DeriveJsonEncoder.gen[RouteMessage]
  }
}
