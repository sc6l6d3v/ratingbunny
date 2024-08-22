package com.iscs.ratingslave

package object domains {

  trait AutoRecBase

  final case class AutoNameRec(firstName: String, lastName: Option[String]) extends AutoRecBase

  final case class AutoTitleRec(primaryTitle: Option[String]) extends AutoRecBase

  trait TitleRecBase {
    def _id: String
    def averageRating: Option[Double]
    def numVotes: Option[Int]
    def titleType: String
    def primaryTitle: String
    def originalTitle: String
    def isAdult: Int
    def startYear: Int
    def endYear: String
    def runtimeMinutes: Option[Int]
    def genresList: Option[List[String]]
  }

  final case class TitleRec(_id: String, averageRating: Option[Double], numVotes: Option[Int],
                            titleType: String, primaryTitle: String, originalTitle: String,
                            isAdult: Int, startYear: Int, endYear: String, runtimeMinutes: Option[Int],
                            genresList: Option[List[String]]) extends TitleRecBase

  final case class TitleRecPath(_id: String, averageRating: Option[Double], numVotes: Option[Int],
                                titleType: String, primaryTitle: String, originalTitle: String,
                                isAdult: Int, startYear: Int, endYear: String, runtimeMinutes: Option[Int],
                                genresList: Option[List[String]], posterPath: Option[String]) extends TitleRecBase

}
