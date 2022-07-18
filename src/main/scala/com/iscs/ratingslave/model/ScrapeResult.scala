package com.iscs.ratingslave.model

import cats.effect.Sync
import io.circe._
import io.circe.generic.semiauto._
import org.http4s.circe._
import org.http4s.{EntityDecoder, EntityEncoder}

object ScrapeResult {
  final case class Scrape(id: String, title: String, link: String, rating: String)

  final case class ScrapeList(l: List[Scrape])

  object Scrape {
    implicit val scrapeDecoder: Decoder[Scrape] = deriveDecoder[Scrape]
    implicit def scrapeEntityDecoder[F[_]: Sync]: EntityDecoder[F, Scrape] = jsonOf
    implicit def scrapeEncoder: Encoder[Scrape] = deriveEncoder[Scrape]
    implicit def scrapeEntityEncoder[F[_]: Sync]: EntityEncoder[F, Scrape] = jsonEncoderOf
    implicit def scrapeListEncoder: Encoder[List[Scrape]] = deriveEncoder[List[Scrape]]
    implicit def scrapeListEntityEncoder[F[_]: Sync]: EntityEncoder[F, List[Scrape]] = jsonEncoderOf
    implicit def listScrapeEncoder: Encoder[ScrapeList] = deriveEncoder[ScrapeList]
    implicit def listScrapeEntityEncoder[F[_]: Sync]: EntityEncoder[F, ScrapeList] = jsonEncoderOf
  }
}
