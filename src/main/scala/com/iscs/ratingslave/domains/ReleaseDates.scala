package com.iscs.ratingslave.domains

import cats.effect.{Concurrent, ConcurrentEffect}
import com.iscs.ratingslave.model.ScrapeResult.Scrape
import fs2.Stream
import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Element}
import org.log4s.getLogger

import scala.jdk.CollectionConverters._

class ReleaseDates[F[_]: Concurrent](defaultHost: String)(implicit F: ConcurrentEffect[F]) {
  private val L = getLogger
  private val yearRegex = "[0-9][0-9][0-9][0-9]".r
  private val monthRegex = "[0-9][0-9]".r
  private val fieldDoc = "table.fieldtable-inner td.dvdcell"
  private val monthMap = Map("01" -> "january",
    "02" -> "february",
    "03" -> "march",
    "04" -> "april",
    "05" -> "may",
    "06" -> "june",
    "07" -> "july",
    "08" -> "august",
    "09" -> "september",
    "10" -> "october",
    "11" -> "november",
    "12" -> "december",
  )
  private val defaultMap = s"https://$defaultHost/releases/YYYY/MM/new-dvd-releases-MONTH-YYYY"
  private val linkMap = Map("rel" -> s"https://$defaultHost/releases/YYYY/MM/new-dvd-releases-MONTH-YYYY",
    "new" -> s"https://$defaultHost/new-movies-YYYY/",
    "top" -> s"https://$defaultHost/top-movies-YYYY/"
  )

  private def getDocument(urlType: String, year: String, month: String): Document = {
    val goodYear = if (yearRegex.matches(year)) year else "2020"
    val goodMonth = if (monthRegex.matches(month)) month else "01"
    val finalURL = linkMap.get(urlType).getOrElse(defaultMap)
      .replaceAll("YYYY", goodYear)
      .replace("MM", goodMonth)
      .replace("MONTH", monthMap(goodMonth))
    Jsoup.connect(finalURL).get()
  }

  private def getDocument(urlType: String, year: String): Document = {
    val goodYear = if (yearRegex.matches(year)) year else "2020"
    val finalURL = linkMap.get(urlType).getOrElse(defaultMap)
      .replaceAll("YYYY", goodYear)
    Jsoup.connect(finalURL).get()
  }

  private def processElement(e: Element, minRating: Double): Option[Scrape] = {
    val links = e.select("a").asScala
    val title = links(1).text
    val textRating = links(2).text
    val rating = textRating match {
      case "NA" => 0
      case default => default.toDouble
    }
    if(rating > minRating && !title.contains("Season")) {
      val id = links(2).attr("href").split("/").last
      Some(Scrape(id, links(1).text, links(2).attr("href"), links(2).text))
    } else
      None
  }

  def findReleases(urlType: String, year: String, month: String, minRating: Double): Stream[F, Scrape] = for {
    indexDoc <- Stream.eval(Concurrent[F].delay(getDocument(urlType, year, month)))
    elt <- Stream.emits(indexDoc.select(fieldDoc).asScala.toList)
    scrape <- Stream.eval(Concurrent[F].delay(processElement(elt, minRating)))
        .filter(_.isDefined)
        .map(_.get)
  } yield scrape

  def findMovies(urlType: String, year: String, minRating: Double): Stream[F, Scrape] = for {
    indexDoc <- Stream.eval(Concurrent[F].delay(getDocument(urlType, year)))
    elt <- Stream.emits(indexDoc.select(fieldDoc).asScala.toList)
    scrape <- Stream.eval(Concurrent[F].delay(processElement(elt, minRating)))
      .filter(_.isDefined)
      .map(_.get)
  } yield scrape
}