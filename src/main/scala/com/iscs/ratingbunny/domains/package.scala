package com.iscs.ratingbunny

package object domains {

  final private case class PathRec(path: String)

  trait AutoRecBase

  final case class AutoNameRec(firstName: String, lastName: Option[String]) extends AutoRecBase

  final case class AutoTitleRec(primaryTitle: Option[String]) extends AutoRecBase

  trait TitleRecBase {
    val _id: String
    val averageRating: Option[Double]
    val numVotes: Option[Int]
    val titleType: String
    val primaryTitle: String
    val originalTitle: String
    val isAdult: Int
    val startYear: Int
    val endYear: String
    val runtimeMinutes: Option[Int]
    val genresList: Option[List[String]]
  }

  final case class TitleRec(
      _id: String,
      averageRating: Option[Double],
      numVotes: Option[Int],
      titleType: String,
      primaryTitle: String,
      originalTitle: String,
      isAdult: Int,
      startYear: Int,
      endYear: String,
      runtimeMinutes: Option[Int],
      genresList: Option[List[String]]
  ) extends TitleRecBase

  final case class TitleRecPath(
      _id: String,
      averageRating: Option[Double],
      numVotes: Option[Int],
      titleType: String,
      primaryTitle: String,
      originalTitle: String,
      isAdult: Int,
      startYear: Int,
      endYear: String,
      runtimeMinutes: Option[Int],
      genresList: Option[List[String]],
      posterPath: Option[String]
  ) extends TitleRecBase

}
