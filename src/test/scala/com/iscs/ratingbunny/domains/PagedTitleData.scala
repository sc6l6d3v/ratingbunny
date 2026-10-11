package com.iscs.ratingbunny.domains

import mongo4cats.bson.syntax.*
import mongo4cats.bson.{BsonValue, Document}

/** Rated title fixtures for paging tests. Votes fall strictly as `i` rises, so the by-rating sort order is ascending `i`; averages
  * alternate between 7.5 and 8.5 so a rating floor of 8.0 keeps every second title.
  */
object PagedTitleData:
  def titleDoc(i: Int, id: String, nconst: Option[String] = None): Document =
    val doc = Document(
      "_id"            := id,
      "tconst"         := f"tt$i%07d",
      "titleType"      := "movie",
      "primaryTitle"   := f"Paged $i%02d",
      "originalTitle"  := f"Paged $i%02d",
      "primaryTitleLC" := f"paged $i%02d",
      "isAdult"        := 0,
      "rated"          := BsonValue.True,
      "hasUS"          := BsonValue.True,
      "startYear"      := 2001d + i,
      "genres"         := List("Drama"),
      "rating"         := Document("average" := (if i % 2 == 0 then 8.5d else 7.5d), "votes" := 50000d - i)
    )
    nconst.fold(doc)(nm => doc.add("nconst" := nm))

  val pagedTitleRecs: List[Document] = (1 to 27).toList.map(i => titleDoc(i, f"tt$i%07d"))
