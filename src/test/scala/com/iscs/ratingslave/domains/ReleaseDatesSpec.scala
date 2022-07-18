package com.iscs.ratingslave.domains

import org.jsoup.Jsoup
import org.scalatest.wordspec.AnyWordSpec
import scala.jdk.CollectionConverters._

class ReleaseDatesSpec extends AnyWordSpec {
  private val fieldDoc = "table.fieldtable-inner td.dvdcell"
  private val name = "new-dvd-releases-december-2020.html"
  private val htmlPath = os.resource() / name
  private val htmlFile = os.read.lines(htmlPath)
  private val doc = Jsoup.parse(htmlFile.mkString("\n"))
  private val elts = doc.select(fieldDoc).asScala.toList

  "file of URL" when  {
    "read" should {
      "not be empty" in {
        assert(elts.size == 35)
      }

      "have <a> tags" in {
        val links = elts.head.select("a").asScala
        assert(links.size > 0)
      }
    }
  }
}
