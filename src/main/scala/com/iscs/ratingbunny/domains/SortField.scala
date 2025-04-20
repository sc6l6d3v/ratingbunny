package com.iscs.ratingbunny.domains

enum SortField:
  case ByRating, ByYear, ByTitle, ByVotes

object SortField:
  def from(opt: Option[String]): SortField = opt match
    case Some("rating") => ByRating
    case Some("year")   => ByYear
    case Some("title")  => ByTitle
    case Some("votes")  => ByVotes
    case _              => ByRating
