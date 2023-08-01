package com.iscs.ratingslave.domains

import org.mongodb.scala.bson.{BsonBoolean, BsonDocument, BsonDouble, BsonElement, BsonInt32, BsonRegularExpression, BsonString, BsonValue}

trait FilterHelper {
  val averageRating = "averageRating"
  val startYear = "startYear"
  val primaryTitle = "primaryTitle"
  val titleType = "titleType"
  val isAdult = "isAdult"
  val numVotes = "numVotes"
  val genresList = "genresList"

  def composeElts(elts: List[BsonElement]): List[(String, BsonValue)] = elts.map(elt => (elt.getName, elt.getValue))

  def extractElt(elt: BsonElement): (String, BsonValue) = (elt.getName, elt.getValue)

  private val NaN: BsonValue = BsonString("NaN")

  def fieldNotNaN(fieldName: String): BsonElement =
    BsonElement(fieldName,
      BsonDocument("$not" ->
        BsonDocument("$eq" ->
          BsonDocument("$numberDecimal" -> NaN))))

  def dblGte(fieldName: String, rating: Double): BsonElement = BsonElement(fieldName, BsonDocument("$gte" -> BsonDouble(rating)))

  def betweenYearsMap(fieldName: String, startyear: Int, stopyear: Int): BsonElement = {
    val yrRange: BsonValue = BsonDocument(
      "$gte" -> BsonInt32(startyear),
      "$lte" -> BsonInt32(stopyear)
    )
    BsonElement(fieldName, yrRange)
  }

  def strEq(fieldName: String, str: String): BsonElement = BsonElement(fieldName, BsonString(str))

  def strExists(fieldName: String, tf: Boolean = true): BsonElement = BsonElement(fieldName, BsonDocument("$exists" -> BsonBoolean(tf)))

  def strNe(fieldName: String, str: String): BsonElement = BsonElement(fieldName, BsonDocument("$ne" -> BsonString(str)))

  def inOrEqList(fieldName: String, inputList: List[String]): BsonElement = inputList match {
    case manyElements: List[String] if manyElements.size > 1    => BsonElement(fieldName, BsonDocument("$in" -> manyElements))
    case singleElement: List[String] if singleElement.size == 1 => BsonElement(fieldName, BsonString(singleElement.head))
  }

  def searchText(msg: String): BsonElement = BsonElement("$text", BsonDocument("$search" -> BsonString(msg)))

  def searchTitle(fieldName: String, msg: String): BsonElement = BsonElement(fieldName, BsonDocument("$regex" -> BsonRegularExpression(msg)))

  def isIntElt(fieldName: String, isOn: Int): BsonElement = BsonElement(fieldName, BsonInt32(isOn))

  def numVotesMap(fieldName: String, votes: Int): BsonElement = BsonElement(fieldName, BsonDocument("$gte" -> BsonInt32(votes)))
}
