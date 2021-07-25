package com.iscs.releaseScraper.model

import cats.effect.Sync
import io.circe._
import io.circe.generic.semiauto._
import org.http4s.circe._
import org.http4s.{EntityDecoder, EntityEncoder}

object RouteMessage {
  final case class RouteMessage(message: String)

  object RouteMessage {
    implicit val scrapeDecoder: Decoder[RouteMessage] = deriveDecoder[RouteMessage]
    implicit def scrapeEntityDecoder[F[_]: Sync]: EntityDecoder[F, RouteMessage] = jsonOf
    implicit def scrapeEncoder: Encoder[RouteMessage] = deriveEncoder[RouteMessage]
    implicit def scrapeEntityEncoder[F[_]: Sync]: EntityEncoder[F, RouteMessage] = jsonEncoderOf
  }
}
