package com.iscs.ratingslave.model

import zio.json._
import zio.json.interop.http4s._
import cats.effect.{Concurrent, Sync}
/*import io.circe._
import io.circe.generic.semiauto._
import org.http4s.circe._ */
import org.http4s.{EntityDecoder, EntityEncoder}

object RouteMessage {
  final case class RouteMessage(message: String)

  object RouteMessage {
    implicit val scrapeDecoder: JsonDecoder[RouteMessage] = DeriveJsonDecoder.gen[RouteMessage]
    // implicit val scrapeDecoder: Decoder[RouteMessage] = deriveDecoder[RouteMessage]
    implicit def scrapeEntityDecoder[F[_]: Sync: Concurrent]: EntityDecoder[F, RouteMessage] = jsonOf[F, RouteMessage]
    implicit def scrapeEncoder: JsonEncoder[RouteMessage] = DeriveJsonEncoder.gen[RouteMessage]
    // implicit def scrapeEncoder: Encoder[RouteMessage] = deriveEncoder[RouteMessage]
    implicit def scrapeEntityEncoder[F[_]: Sync]: EntityEncoder[F, RouteMessage] = jsonEncoderOf[F, RouteMessage]
  }
}
