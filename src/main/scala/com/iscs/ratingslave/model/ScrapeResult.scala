package com.iscs.ratingslave.model

import cats.effect.{Concurrent, Sync}
import org.http4s.{EntityDecoder, EntityEncoder}
import zio.json._
import zio.json.interop.http4s._

object ScrapeResult {
  final case class Scrape(id: String, title: String, link: String, rating: String)

  final case class ScrapeList(l: List[Scrape])

  object Scrape {
    implicit val scrapeDecoder: JsonDecoder[Scrape] = DeriveJsonDecoder.gen[Scrape]
    // implicit val scrapeDecoder: Decoder[Scrape] = deriveDecoder[Scrape]
    implicit def scrapeEntityDecoder[F[_]: Sync: Concurrent]: EntityDecoder[F, Scrape] = jsonOf
    implicit def scrapeEncoder: JsonEncoder[Scrape] = DeriveJsonEncoder.gen[Scrape]
    // implicit def scrapeEncoder: Encoder[Scrape] = deriveEncoder[Scrape]
    // implicit def scrapeEntityEncoder[F[_]: Sync]: EntityEncoder[F, Scrape] = jsonEncoderOf
    implicit def scrapeListEncoder: JsonEncoder[List[Scrape]] = DeriveJsonEncoder.gen[List[Scrape]]
    // implicit def scrapeListEncoder: Encoder[List[Scrape]] = deriveEncoder[List[Scrape]]
    implicit def scrapeListEntityEncoder[F[_]: Sync]: EntityEncoder[F, List[Scrape]] = jsonEncoderOf
    implicit def listScrapeEncoder: JsonEncoder[ScrapeList] = DeriveJsonEncoder.gen[ScrapeList]
    // implicit def listScrapeEncoder: Encoder[ScrapeList] = deriveEncoder[ScrapeList]
    implicit def listScrapeEntityEncoder[F[_]: Sync]: EntityEncoder[F, ScrapeList] = jsonEncoderOf
  }
}
