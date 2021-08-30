package com.iscs.releaseScraper.domains

import cats.effect.{Concurrent, ConcurrentEffect, Sync}
import cats.implicits._
import com.iscs.releaseScraper.model.Requests.ReqParams
import com.iscs.releaseScraper.util.{DbClient, asInt}
import com.typesafe.scalalogging.Logger
import fs2.{Pipe, Stream}
import io.circe._
import io.circe.generic.semiauto._
import io.circe.parser._
import org.bson.conversions.Bson
import org.http4s.circe._
import org.http4s.{EntityDecoder, EntityEncoder}
import org.mongodb.scala.bson.{BsonDocument, BsonNumber, conversions}
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Filters.{and, exists, gte, in, lte, regex, text, eq => mdbeq, ne => mdne}
import org.mongodb.scala.model._

import scala.language.implicitConversions

abstract class QueryObj
case object TitleQuery extends QueryObj
case object NameQuery extends QueryObj

trait ImdbQuery[F[_]] {
  def getByTitle(title: String, rating: Double, params: ReqParams): Stream[F,Json]
  def getByName(name: String, rating: Double, params: ReqParams): Stream[F,Json]
  def getAutosuggestTitle(titlePrefix: String): Stream[F,Json]
  def getAutosuggestName(titlePrefix: String): Stream[F,Json]
}

object ImdbQuery {
  private val L = Logger[this.type]

  def apply[F[_]](implicit ev: ImdbQuery[F]): ImdbQuery[F] = ev

  final case class TitleRec(averageRating: Option[Double], numVotes: Option[Int],
                            titleType: String, primaryTitle: String, originalTitle: String,
                            isAdult: Int, startYear: Int, endYear: String, runTimeMinutes: Int,
                            genresList: List[String])

  final case class NameTitleRec(primaryName: String, birthYear: Int,
                                matchedTitles: List[TitleRec])

  object TitleRec {
    implicit val titleRecDecoder: Decoder[TitleRec] = deriveDecoder[TitleRec]
    implicit def titleRecEntityDecoder[F[_]: Sync]: EntityDecoder[F, TitleRec] = jsonOf
    implicit val titleRecEncoder: Encoder[TitleRec] = deriveEncoder[TitleRec]
    implicit def titleRecEntityEncoder[F[_]: Sync]: EntityEncoder[F, TitleRec] = jsonEncoderOf
  }

  object NameTitleRec {
    implicit val nameTitleRecDecoder: Decoder[NameTitleRec] = deriveDecoder[NameTitleRec]
    implicit def nameTitleRecEntityDecoder[F[_]: Sync]: EntityDecoder[F, NameTitleRec] = jsonOf
    implicit val nameTitleRecEncoder: Encoder[NameTitleRec] = deriveEncoder[NameTitleRec]
    implicit def nameTitleRecEntityEncoder[F[_]: Sync]: EntityEncoder[F, NameTitleRec] = jsonEncoderOf
  }

  def impl[F[_]: Concurrent: Sync: ConcurrentEffect](dbClient: DbClient[F]): ImdbQuery[F] =
    new ImdbQuery[F] {
      val nameCollection = "name_basics"
      val titleCollection = "title_basics_ratings"

      val id = "_id"
      val birthYear = "birthYear"
      val deathYear = "deathYear"
      val firstName = "firstName"
      val lastName = "lastName"
/*      val primaryProfession = "primaryProfession"
      val primaryProfessionList = "primaryProfessionList"*/
      val knownForTitles = "knownForTitles"
      val knownForTitlesList = "knownForTitlesList"
      val primaryTitle = "primaryTitle"
      val primaryName = "primaryName"
      val matchedTitles = "matchedTitles"

      val averageRating = "averageRating"
      val matchedTitles_averageRating = "matchedTitles.averageRating"
      val genres = "genres"
      val matchedTitles_genres = "matchedTitles.genres"
      val genresList = "genresList"
      val matchedTitles_genresList = "matchedTitles.genresList"
      val isAdult = "isAdult"
      val matchedTitles_isAdult = "matchedTitles.isAdult"
      val startYear = "startYear"
      val matchedTitles_startYear = "matchedTitles.startYear"
      val titleType = "titleType"
      val matchedTitles_titleType = "matchedTitles.titleType"
      private val titleFx = dbClient.fxMap(titleCollection)
      private val nameFx = dbClient.fxMap(nameCollection)

      private def docToJson[F[_]: ConcurrentEffect]: Pipe[F, Document, Json] = strDoc => {
        for {
          doc <- strDoc
          json <- Stream.eval(Concurrent[F].delay(parse(doc.toJson()) match {
            case Right(validJson) => validJson
            case Left(parseFailure) =>
              L.error(""""Parsing failure" exception={} """, parseFailure.toString)
              Json.Null
          }))
        } yield json
      }

      def condenseSingleLists(bsonList: List[Bson]): F[Bson] =
        for {
          bson <- Concurrent[F].delay{
            if (bsonList.size == 1) bsonList.head
            else and(bsonList: _*)
          }
        } yield bson

      def getTitleModelFilters(title: String): F[Bson] = for {
        parts <- Concurrent[F].delay(title.split(" ").toList)
        textBson <- Concurrent[F].delay(text(title))
      } yield textBson

      implicit def convertBooleanToInt(b: Boolean): asInt = new asInt(b)

      private def mapQtype(qType: QueryObj, titleString: String, nameString: String): String =
        qType match {
          case TitleQuery => titleString
          case NameQuery  => nameString
        }

      private def inOrEq[T](inputList: List[T], fieldName: String): conversions.Bson = inputList match {
        case manyElements:List[T] if manyElements.size > 1    => in(fieldName, manyElements:_*)
        case singleElement:List[T] if singleElement.size == 1 => mdbeq(fieldName, singleElement.head)
      }

      def getParamList(params: ReqParams, qType: QueryObj): F[List[Bson]] = {
        Concurrent[F].delay(
          List(
            params.year.map(yr => gte(mapQtype(qType, startYear, matchedTitles_startYear), yr.head)),
            params.year.map(yr => lte(mapQtype(qType, startYear, matchedTitles_startYear), yr.last)),
            params.genre.map{ genre =>
              val finalString = mapQtype(qType, genresList, matchedTitles_genresList)
              inOrEq(genre, finalString)
            },
            params.titleType.map { tt =>
              val finalString = mapQtype(qType, titleType, matchedTitles_titleType)
              inOrEq(tt, finalString)
            },
            params.isAdult.map(isadult =>
              mdbeq(mapQtype(qType, isAdult, matchedTitles_isAdult), isadult.toInt))
          ).flatten)
      }

      def getParamModelFilters(params: ReqParams, qType: QueryObj): F[Bson] = for {
        bsonList <- getParamList(params, qType)
        condensedBson <- condenseSingleLists(bsonList)
      } yield condensedBson

      override def getByTitle(title: String, rating: Double, params: ReqParams): Stream[F, Json] = for {
        paramBson <- Stream.eval(getParamModelFilters(params, TitleQuery))
        ratingBson <- Stream.eval(Concurrent[F].delay(gte(averageRating, rating)))
        titleBson <- Stream.eval(getTitleModelFilters(title))
        dbList <- Stream.eval(titleFx.find(
          and(titleBson, ratingBson, paramBson),
        20, 0, Map(genres -> false))
          .through(docToJson)
          .compile.toList)
        json <- Stream.emits(dbList)
      } yield json

      /**
       * {
       *    $match: { $and: [
       *                { 'knownForTitles': { $exists: true, $ne: "" }},
       *                { 'primaryName': 'Tom Cruise' }
       *              ]
       *            }
       * }, {
       *    $lookup: {
       *       'from': 'title_basics_ratings',
       *       'localField': 'knownForTitlesList',
       *       'foreignField': '_id',
       *       'as': 'matchedTitles'
       * }, {
       *    $project: {
       *        "matchedTitles.genres": 0  }
       *    }
       * }, {
       *    $unwind: '$matchedTitles'
       * }, {
       *    $match: { $and: [
       *                    { "matchedTitles.averageRating", 7.0 },
       *                    { "matchedTitles.startYear": {$gte: 1986, $lte: 2001} },
       *                    { "matchedTitles.genresList": {$in: ["Drama", "Comedy"] },
       *                    { "$$matchedTitles.titleType": {$in: ["movie", "tvSeries"] },
       *                    { "$$matchedTitles.isAdult": 1 },
       *              ]
       *            }
       * }, {
       *    $group: {
       *       _id: "$_id",
       *       primaryName: { $first: "$primaryName" },
       *       firstName: { $first: "$firstName" },
       *       lastName: { $first: "$firstName" },
       *       birthYear: { $first: "$birthYear" },
       *       deathYear: { $first: "$deathYear" },
       *       matchedTitles: { $push: "$matchedTitles" },
       *    }
       * }
       *
       * @param name of actor
       * @param rating IMDB
       * @param params body
       * @return
       */
      override def getByName(name: String, rating: Double, params: ReqParams): Stream[F, Json] = for {
        matchTitleWithName <- Stream.eval(Concurrent[F].delay(
          and(
            exists(knownForTitles, exists = true),
            mdne(knownForTitles, ""),
            mdbeq(primaryName, name)
          )
        ))
        titleMatchFilter <- Stream.eval(Concurrent[F].delay(
          Aggregates.filter(matchTitleWithName)
        ))

        lookupFilter <- Stream.eval(Concurrent[F].delay(
          Aggregates.lookup(titleCollection,
            knownForTitlesList,
            "_id",
            matchedTitles)
        ))

        paramsList <- Stream.eval(getParamList(params, NameQuery))
        ratingBson <- Stream.eval(Concurrent[F].delay(gte(matchedTitles_averageRating, rating)))
        bsonCondensedList <- Stream.eval(condenseSingleLists(paramsList :+ ratingBson))
        matchLookupsFilter <- Stream.eval(Concurrent[F].delay(
          Aggregates.filter(and(bsonCondensedList))
        ))

        projectionsList <- Stream.eval(Concurrent[F].delay(
          nameFx.getProjectionFields(Map(
            matchedTitles_genres -> false
          ))
        ))
        projectionFilter <- Stream.eval(Concurrent[F].delay(
          Aggregates.project(nameFx.getProjections(projectionsList))
        ))

        unwindFilter <- Stream.eval(Concurrent[F].delay(
          Aggregates.unwind(s"$$$matchedTitles")
        ))

        groupFilter <- Stream.eval(Concurrent[F].delay(
          Aggregates.group("$_id",
            Accumulators.first(primaryName, s"$$$primaryName"),
            Accumulators.first(firstName, s"$$$firstName"),
            Accumulators.first(lastName, s"$$$lastName"),
            Accumulators.first(birthYear, s"$$$birthYear"),
            Accumulators.first(deathYear, s"$$$deathYear"),
            Accumulators.push(matchedTitles, s"$$$matchedTitles")
          )
        ))

        dbList <- Stream.eval(nameFx.aggregate(
          Seq(
            titleMatchFilter,
            lookupFilter,
            projectionFilter,
            unwindFilter,
            matchLookupsFilter,
            groupFilter
          )
        )
          .through(docToJson)
          .compile.toList)
        json <- Stream.emits(dbList)
      } yield json

      override def getAutosuggestName(namePrefix: String): Stream[F, Json] = for {
        dbList <- Stream.eval(nameFx.find(
          regex(lastName, s"""^$namePrefix"""),
          20, 0,
          Map(
            id -> false,
            lastName -> true,
            firstName -> true
          ),
          BsonDocument(List((primaryTitle, BsonNumber(1)))))
          .through(docToJson)
          .compile.toList)
        json <- Stream.emits(dbList)
      } yield json

      override def getAutosuggestTitle(titlePrefix: String): Stream[F, Json] = for {
        dbList <- Stream.eval(titleFx.find(
          regex(primaryTitle, s"""^$titlePrefix"""),
          20, 0,
          Map(
            id -> false,
            primaryTitle -> true
          ))
          .through(docToJson)
          .compile.toList)
        json <- Stream.emits(dbList)
      } yield json
    }

}
