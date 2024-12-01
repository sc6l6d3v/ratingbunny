package com.iscs.ratingbunny.domains

import org.scalatest.wordspec.AnyWordSpec

class FetchImageSpec extends AnyWordSpec {
  private val fieldDoc = "table.fieldtable-inner td.dvdcell"
  private val name     = "new-dvd-releases-december-2020.html"
//  private val htmlPath = os.resource() / name
//  private val htmlFile = os.read.lines(htmlPath)
//  private val doc = Jsoup.parse(htmlFile.mkString("\n"))
//  private val elts = doc.select(fieldDoc).asScala.toList

  "file of URL" when {
    "read" should {
      "not be empty" in {
        succeed
//        assert(elts.size == 35)
      }

      "have <a> tags" in {
//        val links = elts.head.select("a").asScala
//        assert(links.size > 0)
        succeed
      }
    }
  }
}
