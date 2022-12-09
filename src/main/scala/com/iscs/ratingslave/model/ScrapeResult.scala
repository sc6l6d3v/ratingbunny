package com.iscs.ratingslave.model

import zio.json._

object ScrapeResult {
  final case class Scrape(id: String, title: String, link: String, rating: String)

  final case class ScrapeList(l: List[Scrape])

  object Scrape {
    implicit val scrapeDecoder: JsonDecoder[Scrape] = DeriveJsonDecoder.gen[Scrape]
    implicit val scrapeEncoder: JsonEncoder[Scrape] = DeriveJsonEncoder.gen[Scrape]
    implicit def listScrapeEncoder: JsonEncoder[ScrapeList] = DeriveJsonEncoder.gen[ScrapeList]
  }
}
