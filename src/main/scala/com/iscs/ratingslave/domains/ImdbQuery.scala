package com.iscs.ratingslave.domains

import cats.effect._
import cats.effect.implicits._
import cats.effect.kernel.Clock
import cats.implicits._
import com.iscs.ratingslave.domains.ImdbQuery.{AutoNameRec, AutoTitleRec, NameTitleRec, TitleRec, TitleRecPath}
import com.iscs.ratingslave.model.Requests.ReqParams
import com.iscs.ratingslave.util.{DecodeUtils, asInt}
import com.typesafe.scalalogging.Logger
import fs2.Stream
import io.circe.generic.auto._
import mongo4cats.circe._
import mongo4cats.collection.MongoCollection
import mongo4cats.operations.Projection
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.{Method, Request, Uri}
import org.mongodb.scala.bson.{BsonArray, BsonDocument, BsonElement, BsonInt32}
import org.mongodb.scala.model.Projections._
import org.mongodb.scala.model.{Accumulators, Aggregates, Projections}

import scala.language.implicitConversions

abstract class QueryObj
case object TitleQuery extends QueryObj
case object NameQuery extends QueryObj

trait ImdbQuery[F[_]] {
  def getByTitle(title: Option[String], rating: Double, params: ReqParams): Stream[F, TitleRec]
  def getByTitlePath(title: Option[String], rating: Double, params: ReqParams): Stream[F, TitleRecPath]
  def getByName(name: String, rating: Double, params: ReqParams): Stream[F, NameTitleRec]
  def getByEnhancedName(name: String, rating: Double, params: ReqParams): Stream[F, TitleRec]
  def getAutosuggestTitle(titlePrefix: String): Stream[F, AutoTitleRec]
  def getAutosuggestName(titlePrefix: String): Stream[F, AutoNameRec]
}

object ImdbQuery extends FilterHelper with DecodeUtils {
  private val L = Logger[this.type]

  private val AUTOSUGGESTLIMIT = 20

  def apply[F[_]](implicit ev: ImdbQuery[F]): ImdbQuery[F] = ev

  final case class AutoNameRec(firstName: String, lastName: Option[String])

  final case class AutoTitleRec(primaryTitle: Option[String])

  private final case class PathRec(path: String)

  final case class TitleRec(_id: String, averageRating: Option[Double], numVotes: Option[Int],
                            titleType: String, primaryTitle: String, originalTitle: String,
                            isAdult: Int, startYear: Int, endYear: String, runTimeMinutes: Option[Int],
                            genresList: List[String])

  final case class TitleRecPath(_id: String, averageRating: Option[Double], numVotes: Option[Int],
                            titleType: String, primaryTitle: String, originalTitle: String,
                            isAdult: Int, startYear: Int, endYear: String, runTimeMinutes: Option[Int],
                            genresList: List[String], posterPath: String)

  final case class NameTitleRec(primaryName: String, firstName: String, lastName: String, birthYear: Int,
                                deathYear1: Option[String] = None,
                                matchedTitles: List[TitleRec])

  def impl[F[_]: Async](titleFx: MongoCollection[F, TitleRec],
                       titlePrincipalsFx: MongoCollection[F, TitleRec],
                       nameFx: MongoCollection[F, AutoNameRec],
                       imageHost: String,
                       client: Client[F]): ImdbQuery[F] =
    new ImdbQuery[F] {
      private val proto = protoc(imageHost)
      private val imagePath = s"$proto://$imageHost/path"
      L.info(s"imageHost {} metaImage {}", imageHost, imagePath)

      val titleCollection = "title_basics_ratings"

      val id = "_id"
      val birthYear = "birthYear"
      val deathYear = "deathYear"
      val firstName = "firstName"
      val lastName = "lastName"
      val knownForTitles = "knownForTitles"
      val knownForTitlesList = "knownForTitlesList"
      val category = "category"
      val tconst = "tconst"
      private val roleList = List("actor", "actress")
      val primaryName = "primaryName"
      val primaryTitle = "primaryTitle"
      val matchedTitles = "matchedTitles"

      val matchedTitles_averageRating = "matchedTitles.averageRating"
      val genres = "genres"
      val matchedTitles_genres = "matchedTitles.genres"
      val matchedTitles_genresList = "matchedTitles.genresList"
      val matchedTitles_isAdult = "matchedTitles.isAdult"
      val matchedTitles_numvotes = "matchedTitles.numVotes"
      val matchedTitles_startYear = "matchedTitles.startYear"
      val matchedTitles_titleType = "matchedTitles.titleType"

      val chunkSize = 256

      def getTitleModelFilters(title: String): F[Option[List[BsonElement]]] = for {
        bsonElts <- Sync[F].delay(
          List(searchTextElt(title), regexElt(primaryTitle, title))
        )
      } yield Some(bsonElts)

      implicit def convertBooleanToInt(b: Boolean): asInt = new asInt(b)

      private def mapQtype(qType: QueryObj, titleString: String, nameString: String): String =
        qType match {
          case TitleQuery => titleString
          case NameQuery  => nameString
        }

      def getParamList(params: ReqParams, qType: QueryObj): F[List[BsonElement]] = {
        val filters: List[Option[BsonElement]] = List(
          params.year.map(yr => betweenYearsMap(mapQtype(qType, startYear, matchedTitles_startYear), yr.head, yr.last)),
          params.genre.map(genre => inOrEqList(mapQtype(qType, genresList, matchedTitles_genresList), genre)),
          params.titleType.map(tt => inOrEqList(mapQtype(qType, titleType, matchedTitles_titleType), tt)),
          params.isAdult.map(isAdlt => isIntElt(mapQtype(qType, isAdult, matchedTitles_isAdult), isAdlt.toInt)),
          params.votes.map(v => numVotesMap(mapQtype(qType, numVotes, matchedTitles_numvotes), v))
        )
        Sync[F].delay(filters.flatten)
      }

      def buildOrCheck(fieldName: String, dbl: Double): BsonElement = {
        BsonElement("$or",
          BsonArray(BsonDocument(extractElt(fieldNotNaN(fieldName))),
            BsonDocument(extractElt(strExists(fieldName, tf = false))),
            BsonDocument(extractElt(dblGte(fieldName, dbl)))))
      }

      def getTitlePaths(titleRecs: List[TitleRec], chunkSize: Int): F[List[TitleRecPath]] = {
        titleRecs
          .grouped(chunkSize) // Split the list into chunks
          .toList
          .traverse { chunk =>
            chunk.parTraverse { titleRec => // Process each chunk in parallel
              for {
                pathRec <- getPath(titleRec._id)
              } yield
                TitleRecPath(
                  titleRec._id,
                  titleRec.averageRating,
                  titleRec.numVotes,
                  titleRec.titleType,
                  titleRec.primaryTitle,
                  titleRec.originalTitle,
                  titleRec.isAdult,
                  titleRec.startYear,
                  titleRec.endYear,
                  titleRec.runTimeMinutes,
                  titleRec.genresList,
                  pathRec.path
                )
            }
          }
          .map(_.flatten) // Flatten the results back to a single list
      }


      private def getOptFilters(optTitle: Option[String]): F[Option[List[BsonElement]]] = optTitle match {
          case Some(title)  => getTitleModelFilters(title)
          case _            => Sync[F].delay(Option.empty[List[BsonElement]])
        }

      /**
       * {
       *    $text:{ $search: "Gone with the W" }
       *    primaryTitle: { $regex: /Gone with the W/ }
       *    startYear: { "$gte":1924 },
       *    startYear: { "$lte":1944 },
       *    genresList: "Crime",
       *    titleType: "movie",
       *    isAdult: 0,
       *    votes: { "$gte":5000 },
       *    $or: [
       *          { $and: [ { averageRating: { $exists: true },  { averageRating: NumberDecimal(NaN) } ] },
       *          { averageRating: { $exists: false },
       *          { averageRating: { $gte: 7.0 }
       *         ],
       * }
       *
       * @param optTitle  optional query param
       * @param rating    required numeric
       * @param params    other params
       * @return
       */
      override def getByTitle(optTitle: Option[String], rating: Double, params: ReqParams): Stream[F, TitleRec] = for {
        paramElt <- Stream.eval(getParamList(params, TitleQuery))
        ratingElt <- Stream.eval(Sync[F].delay(buildOrCheck(averageRating, rating)))
        titleElts <- Stream.eval(getOptFilters(optTitle))
        nonEmptyElts <- Stream.eval(Sync[F].delay {
          List(titleElts, Some(paramElt :+ ratingElt)).flatten.flatten
        })
        bsonElts <-  Stream.eval(Sync[F].delay {BsonDocument(composeElts(nonEmptyElts))})
        (byTitleTime, recList) <- Stream.eval(Clock[F].timed(titleFx.find(bsonElts)
          .skip(0)
          .sortByDesc(startYear)
          .projection(Projection.exclude(genres))
          .stream
          .compile.toList))
        _ <- Stream.eval(Sync[F].delay(L.info(s"getByTitle {} ms", byTitleTime.toMillis)))
        titleRec <- Stream.emits(recList)
      } yield titleRec

      /**
       * ('name_basics').aggregate(
       * [
       *   {
       *      "$match":{
       *                 "knownForTitles":{"$ne":""},
       *                 "primaryName":"Steve McQueen"
       *               }
       *   },{
       *      "$lookup":{
       *          "from":"title_basics_ratings",
       *          "localField":"knownForTitlesList",
       *          "foreignField":"_id",
       *          "as":"matchedTitles"
       *       }
       *   },{
       *       "$project":{
       *           "matchedTitles.genres":0,
       *           "deathYear":0
       *        }
       *   },{
       *       "$unwind":"$matchedTitles"
       *   },{
       *       "$match":{
       *           "matchedTitles.startYear":{"$gte":2010,"$lte":2018},
       *           "matchedTitles.genresList":{"$in":["Action","Drama","Horror"]},
       *           "matchedTitles.titleType":"movie",
       *           "$or":[
       *                  { "matchedTitles.averageRating":{"$not":{"$eq":{"$numberDecimal":"NaN"}}}},
       *                  { "matchedTitles.averageRating":{"$exists":false}},
       *                  { "matchedTitles.averageRating":{"$gte":4.0}}
       *                 ]
       *       }
       *   },{
       *       "$group":{
       *           "_id":"$_id",
       *           "primaryName":{"$first":"$primaryName"},
       *           "firstName":{"$first":"$firstName"},
       *           "lastName":{"$first":"$lastName"},
       *           "birthYear":{"$first":"$birthYear"},
       *           "deathYear":{"$first":"$deathYear"},
       *           "matchedTitles":{"$push":"$matchedTitles"}
       *         }
       *   }
       * ])
       *
       * @param name first lastname
       * @param rating IMDB
       * @param params object
       * @return stream of object
       */
      override def getByName(name: String, rating: Double, params: ReqParams): Stream[F, NameTitleRec] = for {
        paramsElt <- Stream.eval(getParamList(params, NameQuery))
        ratingElt <- Stream.eval(Sync[F].delay(
          paramsElt :+ buildOrCheck(matchedTitles_averageRating, rating)
        ))
        projectionsList2 <- Stream.eval(Sync[F].delay(Projections.exclude(matchedTitles_genres,deathYear)))
        matchTitleWithNameElt <- Stream.eval(Sync[F].delay(
          List(
            strExists(knownForTitles),
            strNe(knownForTitles, ""),
            strEq(primaryName, name)
          )
        ))
        aggElt <- Stream.eval(Sync[F].delay(
          Seq(
            Aggregates.`match`(BsonDocument(composeElts(matchTitleWithNameElt))),
            Aggregates.lookup(titleCollection,
              knownForTitlesList,
              "_id",
              matchedTitles),
            Aggregates.project(projectionsList2),
            Aggregates.unwind(s"$$$matchedTitles"),
            Aggregates.`match`(BsonDocument(composeElts(ratingElt))),
            Aggregates.group("$_id",
              Accumulators.first(primaryName, s"$$$primaryName"),
              Accumulators.first(firstName, s"$$$firstName"),
              Accumulators.first(lastName, s"$$$lastName"),
              Accumulators.first(birthYear, s"$$$birthYear"),
              Accumulators.first(deathYear, s"$$$deathYear"),
              Accumulators.push(matchedTitles, s"$$$matchedTitles"))
          )
        ))
        (byNameTime, dbList) <- Stream.eval(Clock[F].timed(
          nameFx.aggregateWithCodec[NameTitleRec](aggElt)
            .stream
            .compile.toList))
        _ <- Stream.eval(Sync[F].delay(L.info(s"getByName {} ms", byNameTime.toMillis)))
        json <- Stream.emits(dbList)
      } yield json

      /**
       *  ('title_principals_withname').aggregate([
       *     {
       *       "$match":{
       *          "primaryName":"June Lockhart",
       *          "category":{"$in":["actor","actress"]}
       *        }
       *     },{
       *       "$lookup":{
       *          "from":"title_basics_ratings",
       *          "localField":"tconst",
       *          "foreignField":"_id",
       *          "as":"matchedTitles"
       *        }
       *    },{
       *      "$match":{
       *         "matchedTitles.startYear":{"$gte":1937,"$lte":1979},
       *         "matchedTitles.genresList":"Comedy",
       *         "matchedTitles.isAdult":0,
       *         "matchedTitles.numVotes":{"$gte":0},
       *         "$or":[
       *             {"matchedTitles.averageRating":{"$not":{"$eq":{"$numberDecimal":"NaN"}}}},
       *             {"matchedTitles.averageRating":{"$exists":false}},
       *             {"matchedTitles.averageRating":{"$gte":4.0}}
       *          ]
       *        }
       *   },{
       *        "$unwind":"$matchedTitles"
       *   },{
       *        "$group":{
       *           "_id":"$_id",
       *           "matchedTitles":{"$push":"$matchedTitles"}
       *        }
       *   },{
       *        "$unwind":"$matchedTitles"
       *   },{
       *        "$replaceWith":"$matchedTitles"
       *   },{
       *        "$project":{"genres":0}
       *   }
       * ])
       * @param name first lastname
       * @param rating IMDB
       * @param params object
       * @return stream object
       */
      override def getByEnhancedName(name: String, rating: Double, params: ReqParams): Stream[F, TitleRec] = for {
        paramElts <- Stream.eval(getParamList(params, NameQuery))
        ratingElt <- Stream.eval(Sync[F].delay(buildOrCheck(matchedTitles_averageRating, rating)))
        bsonCondensedElts <- Stream.eval(Sync[F].delay(paramElts :+ ratingElt))
        matchNameAndElt <- Stream.eval(Sync[F].delay(
          List(strEq(primaryName, name),
            inOrEqList(category, roleList)
          )
        ))

        aggregationElt <- Stream.eval(Sync[F].delay(
          Seq(
            Aggregates.`match`(BsonDocument(composeElts(matchNameAndElt))),
            Aggregates.lookup(titleCollection,
              tconst,
              "_id",
              matchedTitles),
            Aggregates.`match`(BsonDocument(composeElts(bsonCondensedElts))),
            Aggregates.unwind(s"$$$matchedTitles"),
            Aggregates.group("$_id",
              Accumulators.push(matchedTitles, s"$$$matchedTitles")),
            Aggregates.unwind(s"$$$matchedTitles"),
            Aggregates.replaceWith(s"""$$$matchedTitles"""),
            Aggregates.project(BsonDocument(List((genres, BsonInt32(0)))))
          )))

        (byEnhancedNameTime, dbList) <- Stream.eval(Clock[F].timed(
          titlePrincipalsFx.aggregateWithCodec[TitleRec](aggregationElt)
            .stream
            .compile.toList))
        _ <- Stream.eval(Sync[F].delay(L.info(s"getByEnhancedName {} ms", byEnhancedNameTime.toMillis)))
        json <- Stream.emits(dbList.sortBy(_.startYear)(Ordering.Int.reverse))
      } yield json

      /**
       * ('name_basics').aggregate([
       *   {"$match": {"lastName": {$regex: /%Caret%Cra/, "firstName": "Daniel"}}},
       *   {"$project": {"firstName": 1, "lastName": 1}},
       *   {"$group": {"_id": "$lastName",
       *               "firstName": {"$first": "$firstName"}}},
       *   {"$sort": {"_id": 1, "firstName": 1}}},
       *   {"$project": {"_id": 0, "firstName": 1, "lastName": "$_id"}},
       *   {"$limit": 20}
       * ])
       * @param namePrefix first prefix
       * @return stream of object
       */
      override def getAutosuggestName(namePrefix: String): Stream[F, AutoNameRec] = for {
        names <- Stream.eval(Sync[F].delay(
          if (namePrefix contains " ")
            namePrefix.split(" ").toList
          else
            List(namePrefix)
        ))

        lastFirstElt <- Stream.eval(
          if (names.size == 1)
            Sync[F].delay(List(regexElt(lastName, s"""^${names.head}""")))
          else
            Sync[F].delay(List(
              regexElt(lastName, s"""^${names.last}"""),
              strEq(firstName, names.head)))
        )

        projectionsList <- Stream.eval(Sync[F].delay(include(firstName, lastName)))

        sortElt <- Stream.eval(Sync[F].delay(List(sortElt(id), sortElt(firstName))))

        lastProjectionsBson <- Stream.eval(Sync[F].delay(
          fields(
            exclude(id),
            include(firstName),
            computed(lastName, "$_id")
          )
        ))

        aggregation <- Stream.eval(Sync[F].delay(
          Seq(
            Aggregates.`match`(BsonDocument(composeElts(lastFirstElt))),
            Aggregates.project(projectionsList),
            Aggregates.group("$lastName",
              Accumulators.first(firstName, s"$$$firstName")),
            Aggregates.sort(BsonDocument(composeElts(sortElt))),
            Aggregates.project(lastProjectionsBson),
            Aggregates.limit(AUTOSUGGESTLIMIT)
          )
        ))

        (autosuggestTime, dbList) <- Stream.eval(Clock[F].timed(
          nameFx.aggregateWithCodec[AutoNameRec](aggregation)
            .stream
            .compile.toList))
        _ <- Stream.eval(Sync[F].delay(L.info(s"getAutosuggestName {} ms", autosuggestTime.toMillis)))
        json <- Stream.emits(dbList)
      } yield json

      /**
       * ('title_basics_ratings').aggregate([
       *     {"$match": {"$text": {"$search": "Gone with the W"}, "primaryTitle": {$regex: /%caret%Gone with the W/}}},
       *     {"$project": {"_id": 0, "primaryTitle": 1}},
       *     {"$group": {"_id": "$primaryTitle", "primaryTitle": {"$first": "$primaryTitle"}}},
       *     {"$sort": {"primaryTitle": 1}},
       *     {"$limit": 20},
       *     {"$project": {"_id": 0, "primaryTitle": 1}}
       * ])
       * @param titlePrefix title regex
       * @return stream of object
       */
      override def getAutosuggestTitle(titlePrefix: String): Stream[F, AutoTitleRec] = for {
        titleTextElts <- Stream.eval(Sync[F].delay(
          List(searchTextElt(titlePrefix), regexElt(primaryTitle, s"""^$titlePrefix"""))
        ))

        projectionsList <- Stream.eval(Sync[F].delay(fields(exclude(id), include(primaryTitle))))
        sortElt <- Stream.eval(Sync[F].delay(sortElt(primaryTitle)))
        aggregation <- Stream.eval(Sync[F].delay(
          Seq(
            Aggregates.`match`(BsonDocument(composeElts(titleTextElts))),
            Aggregates.project(projectionsList),
            Aggregates.group("$primaryTitle",
              Accumulators.first(primaryTitle, s"$$$primaryTitle")),
            Aggregates.sort(BsonDocument(extractElt(sortElt))),
            Aggregates.limit(AUTOSUGGESTLIMIT),
            Aggregates.project(projectionsList)
          )
        ))

        (autosuggestTitleTime, dbList) <- Stream.eval(Clock[F].timed(
          titleFx.aggregateWithCodec[AutoTitleRec](aggregation)
            .stream
            .compile.toList))
        _ <- Stream.eval(Sync[F].delay(L.info(s"getAutosuggestTitle {} ms", autosuggestTitleTime.toMillis)))
        json <- Stream.emits(dbList)
      } yield json

      private def getPath(imdb: String): F[PathRec] = for {
        pathStr <- Sync[F].delay(s"$imagePath/$imdb/S")
        pathReq <- Sync[F].delay(Request[F](Method.GET, Uri.unsafeFromString(pathStr)))
        (pathTime, pathVal) <- Clock[F].timed(client.expect(pathReq)(jsonOf[F, PathRec]))
        _ <- Sync[F].delay(L.info(s"pathStr {} imdb {} time {} ms",
          pathStr, imdb, pathTime.toMillis))
      } yield pathVal

      override def getByTitlePath(optTitle: Option[String], rating: Double, params: ReqParams): Stream[F, TitleRecPath] = for {
        paramElt <- Stream.eval(getParamList(params, TitleQuery))
        ratingElt <- Stream.eval(Sync[F].delay(buildOrCheck(averageRating, rating)))
        titleElts <- Stream.eval(getOptFilters(optTitle))
        nonEmptyElts <- Stream.eval(Sync[F].delay {
          List(titleElts, Some(paramElt :+ ratingElt)).flatten.flatten
        })
        bsonElts <- Stream.eval(Sync[F].delay {
          BsonDocument(composeElts(nonEmptyElts))
        })
        (byTitleTime, recList) <- Stream.eval(Clock[F].timed(titleFx.find(bsonElts)
          .skip(0)
          .sortByDesc(startYear)
          .projection(Projection.exclude(genres))
          .stream
          .compile.toList))
        _ <- Stream.eval(Sync[F].delay(L.info(s"getByTitle {} ms", byTitleTime.toMillis)))
        recWithPathList <- Stream.eval(getTitlePaths(recList, chunkSize))
        titlePathRec <- Stream.emits(recWithPathList)
      } yield titlePathRec
    }
}
