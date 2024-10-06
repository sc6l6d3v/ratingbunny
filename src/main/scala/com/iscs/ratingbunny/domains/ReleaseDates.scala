package com.iscs.ratingbunny.domains

import cats.effect.kernel.Clock
import cats.effect.{Async, Sync}
import cats.implicits._
import com.iscs.ratingbunny.model.ScrapeResult.Scrape
import com.iscs.ratingbunny.util.DecodeUtils
import com.typesafe.scalalogging.Logger
import fs2.Stream
import org.http4s.client.Client
import org.http4s.{Method, Request, Uri}
import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Element}
import scodec.bits.ByteVector

import java.time.LocalDate
import scala.jdk.CollectionConverters._

class ReleaseDates[F[_]: Async](defaultHost: String, imageHost: String, client: Client[F]) extends DecodeUtils {
  private val L          = Logger[this.type]
  private val yearRegex  = "[0-9][0-9][0-9][0-9]".r
  private val monthRegex = "[0-9][0-9]".r
  private val fieldDoc   = "table.fieldtable-inner td.dvdcell"
  private val monthMap = Map(
    "01" -> "january",
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
    "12" -> "december"
  )
  private val defaultMap = s"https://$defaultHost/releases/YYYY/MM/new-dvd-releases-MONTH-YYYY"
  private val linkMap = Map(
    "rel" -> s"https://$defaultHost/releases/YYYY/MM/new-dvd-releases-MONTH-YYYY",
    "new" -> s"https://$defaultHost/new-movies-YYYY/",
    "top" -> s"https://$defaultHost/top-movies-YYYY/"
  )
  private val proto = protoc(
    imageHost
  ) // if (List("tmdbimg", "localhost", "192.168.4").exists(host => imageHost startsWith host)) "http" else  "https"
  private val metaImage = s"$proto://$imageHost/meta"
  L.info(s"imageHost {} metaImage {}", imageHost, metaImage)
  private val now      = LocalDate.now
  private val curYear  = now.getYear.toString
  private val curMonth = f"${now.getMonthValue}%02d"

  private def getDocument(urlType: String, year: String, maybeMonth: Option[String] = None): F[Document] = for {
    validYear <- Sync[F].delay(if (yearRegex.matches(year)) year else curYear)
    finalURL <- maybeMonth match {
      case Some(month) =>
        val validMonth = if (monthRegex.matches(month)) month else curMonth
        Sync[F].delay(
          linkMap
            .getOrElse(urlType, defaultMap)
            .replaceAll("YYYY", validYear)
            .replace("MM", validMonth)
            .replace("MONTH", monthMap(validMonth))
        )
      case None =>
        Sync[F].delay(
          linkMap
            .getOrElse(urlType, defaultMap)
            .replaceAll("YYYY", validYear)
        )
    }
    doc <- Sync[F].blocking(Jsoup.connect(finalURL).get())
  } yield doc

  private def processElement(e: Element, minRating: Double): F[Option[Scrape]] = for {
    links      <- Sync[F].delay(e.select("a").asScala)
    title      <- Sync[F].delay(links(1).text)
    textRating <- Sync[F].delay(links(2).text)
    rating <- textRating match {
      case "NA"    => Sync[F].pure(0d)
      case default => Sync[F].delay(default.toDouble)
    }
    maybeScrape <-
      if (rating > minRating && !title.contains("Season")) {
        val id = links(2).attr("href").split("/").last
        Sync[F].delay(Some(Scrape(id, links(1).text, links(2).attr("href"), links(2).text)))
      } else
        Sync[F].delay(None)
  } yield maybeScrape

  def getImage(imdb: String): Stream[F, Byte] = for {
    imgStr <- Stream.eval(Sync[F].delay(s"""$metaImage/$imdb/S"""))
    imgReq <- Stream.eval(Sync[F].delay(Request[F](Method.GET, Uri.unsafeFromString(imgStr))))
    (imgTime, imgBytes) <- Stream.eval(
      Clock[F].timed(
        client
          .run(imgReq)
          .use(resp =>
            resp.body.compile
              .to(ByteVector)
              .map(_.toArray)
          )
      )
    )
    _ <- Stream.eval(Sync[F].delay(L.info(s"imgStr {} image id {} size {} time {} ms", imgStr, imdb, imgBytes.length, imgTime.toMillis)))
    imgStream <- Stream.emits(imgBytes)
  } yield imgStream

  def findReleases(urlType: String, year: String, month: String, minRating: Double): Stream[F, Scrape] = for {
    (jsoupAccessTime, indexDoc) <- Stream.eval(Clock[F].timed(getDocument(urlType, year, Some(month))))
    _                           <- Stream.eval(Sync[F].delay(L.info(s"findReleases time {} ms", jsoupAccessTime.toMillis)))
    elt                         <- Stream.emits(indexDoc.select(fieldDoc).asScala.toList)
    scrape <- Stream
      .eval(processElement(elt, minRating))
      .filter(_.isDefined)
      .map(_.get)
  } yield scrape

  def findMovies(urlType: String, year: String, minRating: Double): Stream[F, Scrape] = for {
    (jsoupAccessTime, indexDoc) <- Stream.eval(Clock[F].timed(getDocument(urlType, year)))
    _                           <- Stream.eval(Sync[F].delay(L.info(s""""findMovies time {} ms"""", jsoupAccessTime.toMillis)))
    elt                         <- Stream.emits(indexDoc.select(fieldDoc).asScala.toList)
    scrape <- Stream
      .eval(processElement(elt, minRating))
      .filter(_.isDefined)
      .map(_.get)
  } yield scrape
}
