package com.iscs.ratingslave.model

object Requests {
  final case class ReqParams(
      query: Option[String] = None,
      votes: Option[Int] = None,
      year: Option[List[Int]] = None,
      genre: Option[List[String]] = None,
      titleType: Option[List[String]] = None,
      isAdult: Option[Boolean] = None,
      searchType: Option[String] = None
  ) {
    override def toString: String = List(
      query.map(q => s"query=$q"),
      votes.map(v => s"votes=$v"),
      year.map(yr => s"year=${yr.head} to ${yr.last}"),
      genre.map(g => s"genre=${g.mkString(",")}"),
      titleType.map(t => s"titleType=${t.mkString(",")}"),
      isAdult.map(a => s"isAdult=>$a"),
      searchType.map(s => s"searchType=>$s")
    ).flatten
      .mkString(", ")
  }
}
