package com.iscs.ratingslave.model

object ScrapeResult {
  final case class Scrape(id: String, title: String, link: String, rating: String)
}
