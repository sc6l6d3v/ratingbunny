package com.iscs.releaseScraper.model

import cats.effect.Sync
import io.circe._
import io.circe.generic.semiauto._
import org.http4s.circe._
import org.http4s.{EntityDecoder, EntityEncoder}

object ScrapeResult {
  final case class Scrape(title: String, link: String, rating: String)

  object Scrape {
    implicit val scrapeDecoder: Decoder[Scrape] = deriveDecoder[Scrape]
    implicit def scrapeEntityDecoder[F[_]: Sync]: EntityDecoder[F, Scrape] = jsonOf
    implicit def scrapeEncoder: Encoder[Scrape] = deriveEncoder[Scrape]
    implicit def scrapeEntityEncoder[F[_]: Sync]: EntityEncoder[F, Scrape] = jsonEncoderOf
  }
}
