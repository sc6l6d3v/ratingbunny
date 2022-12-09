package com.iscs.ratingslave.domains

import cats.Applicative
import cats.effect.{Concurrent, Sync}
import com.iscs.ratingslave.domains.ImdbQuery.TitlePrincipals
import com.iscs.ratingslave.util.ProjectionUtils
import mongo4cats.circe._
import mongo4cats.collection.operations.Aggregate.replaceWith
import mongo4cats.collection.operations._
import mongo4cats.collection.operations.Filter._
//import mongo4cats.collection.operations.Projection
import mongo4cats.collection.MongoCollection
import cats.implicits._
import com.iscs.ratingslave.domains.ImdbQuery.{AutoNameRec, AutoTitleRec, TitleRec}
import com.iscs.ratingslave.model.Requests.ReqParams
import com.iscs.ratingslave.util.asInt
import com.typesafe.scalalogging.Logger
import fs2.{Pipe, Stream}
import mongo4cats.bson.{BsonValue, Document}
import mongo4cats.collection.operations.{Accumulator, Aggregate, Sort}
//import org.mongodb.scala.bson.Document
//import org.mongodb.scala.bson.BsonValue
/*import io.circe._
import io.circe.generic.auto._
import io.circe.generic.semiauto._*/
import io.circe.parser._
import org.bson.conversions.Bson
import org.bson.types.Decimal128
//import org.http4s.circe._
//import org.http4s.{EntityDecoder, EntityEncoder}
//import org.mongodb.scala.bson.{BsonDocument, BsonNumber, BsonString, conversions}
//import org.mongodb.scala.model.Aggregates._
//import org.mongodb.scala.model.Filters.{and, exists, gte, in, or, regex, text, eq => mdbeq, ne => mdne}
//import org.mongodb.scala.model._
import scala.jdk.CollectionConverters._
import scala.language.implicitConversions
import zio.json._

abstract class QueryObj
case object TitleQuery extends QueryObj
case object NameQuery extends QueryObj

trait ImdbQuery[F[_]] {
//  def getByTitle(title: Option[String], rating: Double, params: ReqParams): Stream[F, Json]
  def getByTitle2(title: Option[String], rating: Double, params: ReqParams): Stream[F, TitleRec]
//  def getByName(name: String, rating: Double, params: ReqParams): Stream[F, Json]
  def getByName2(name: String, rating: Double, params: ReqParams): Stream[F, TitleRec]
//  def getByEnhancedName(name: String, rating: Double, params: ReqParams): Stream[F, Json]
  def getByEnhancedName2(name: String, rating: Double, params: ReqParams): Stream[F, TitleRec]
//  def getAutosuggestTitle(titlePrefix: String): Stream[F, Json]
  def getAutosuggestTitle2(titlePrefix: String): Stream[F, AutoTitleRec]
//  def getAutosuggestName(titlePrefix: String): Stream[F, Json]
  def getAutosuggestName2(titlePrefix: String): Stream[F, AutoNameRec]
}

object ImdbQuery {
  private val L = Logger[this.type]

  val DOCLIMIT = 200
  val AUTOSUGGESTLIMIT = 20

  def apply[F[_]](implicit ev: ImdbQuery[F]): ImdbQuery[F] = ev

  final case class AutoNameRec(firstName: String, lastname: Option[String])

  final case class AutoTitleRec(primaryTitle: Option[String])

  final case class TitleRec(averageRating: Option[Double], numVotes: Option[Int],
                            titleType: String, primaryTitle: String, originalTitle: String,
                            isAdult: Int, startYear: Int, endYear: String, runTimeMinutes: Option[Int],
                            genresList: List[String])

  final case class  TitlePrincipals(tconst: String, ordering: Int, nconst: String, category: String, job: String,
                                   primaryName: Option[String], firstName: Option[String], lastName: Option[String])

  final case class NameTitleRec(primaryName: String, birthYear: Int,
                                matchedTitles: List[TitleRec])

  object AutoNameRec {
    implicit val autonameRecDecoder: JsonDecoder[AutoNameRec] = DeriveJsonDecoder.gen[AutoNameRec]
//    implicit def autonameRecEntityDecoder[F[_]: Applicative : Concurrent]: EntityDecoder[F, AutoNameRec] = jsonOf
    implicit val autonameRecEncoder: JsonEncoder[AutoNameRec] = DeriveJsonEncoder.gen[AutoNameRec]
//    implicit def autonameRecEntityEncoder[F[_]: Applicative]: EntityEncoder[F, AutoNameRec] = jsonEncoderOf
  }

  object AutoTitleRec {
    implicit val autotitleRecDecoder: JsonDecoder[AutoTitleRec] = DeriveJsonDecoder.gen[AutoTitleRec]
//    implicit def autotitleRecEntityDecoder[F[_]: Applicative : Concurrent]: EntityDecoder[F, AutoTitleRec] = jsonOf
    implicit val autotitleRecEncoder: JsonEncoder[AutoTitleRec] = DeriveJsonEncoder.gen[AutoTitleRec]
//    implicit def autotitleRecEntityEncoder[F[_]: Applicative]: EntityEncoder[F, AutoTitleRec] = jsonEncoderOf
  }

  object TitleRec {
    implicit val titleRecDecoder: JsonDecoder[TitleRec] = DeriveJsonDecoder.gen[TitleRec]
//    implicit def titleRecEntityDecoder[F[_]: Applicative : Concurrent]: EntityDecoder[F, TitleRec] = jsonOf
    implicit val titleRecEncoder: JsonEncoder[TitleRec] = DeriveJsonEncoder.gen[TitleRec]
//    implicit def titleRecEntityEncoder[F[_]: Applicative]: EntityEncoder[F, TitleRec] = jsonEncoderOf
  }

  object TitlePrincipals {
    implicit val titlePrincipalsDecoder: JsonDecoder[TitlePrincipals] = DeriveJsonDecoder.gen[TitlePrincipals]
//    implicit def titlePrincipalsEntityDecoder[F[_]: Applicative : Concurrent]: EntityDecoder[F, TitlePrincipals] = jsonOf
    implicit val titlePrincipalsEncoder: JsonEncoder[TitlePrincipals] = DeriveJsonEncoder.gen[TitlePrincipals]
//    implicit def titlePrincipalsEntityEncoder[F[_]: Applicative]: EntityEncoder[F, TitlePrincipals] = jsonEncoderOf
  }

  object NameTitleRec {
    implicit val nameTitleRecDecoder: JsonDecoder[NameTitleRec] = DeriveJsonDecoder.gen[NameTitleRec]
    implicit val nameTitleRecEncoder: JsonEncoder[NameTitleRec] = DeriveJsonEncoder.gen[NameTitleRec]
  }

  def impl[F[_]: Sync](titleFx: MongoCollection[F, Document],
                       titleFx2: MongoCollection[F, TitleRec],
                       titlePrincipalsFx: MongoCollection[F, Document],
                       titlePrincipalsFx2: MongoCollection[F, TitleRec],
                       nameFx: MongoCollection[F, Document],
                       nameFx2: MongoCollection[F, TitleRec],
                      ): ImdbQuery[F] =
    new ImdbQuery[F] {
      val nameCollection = "name_basics"
      val titleCollection = "title_basics_ratings"
      val titlePrincipalsCollection = "title_principals_withname"

      val id = "_id"
      val birthYear = "birthYear"
      val deathYear = "deathYear1"
      val firstName = "firstName"
      val lastName = "lastName"
      val knownForTitles = "knownForTitles"
      val knownForTitlesList = "knownForTitlesList"
      val category = "category"
      val tconst = "tconst"
      val roleList = List("actor", "actress")
      val primaryName = "primaryName"
      val primaryTitle = "primaryTitle"
      val matchedTitles = "matchedTitles"

      val averageRating = "averageRating"
      val matchedTitles_averageRating = "matchedTitles.averageRating"
      val genres = "genres"
      val matchedTitles_genres = "matchedTitles.genres"
      val genresList = "genresList"
      val matchedTitles_genresList = "matchedTitles.genresList"
      val isAdult = "isAdult"
      val numvotes = "numVotes"
      val matchedTitles_isAdult = "matchedTitles.isAdult"
      val matchedTitles_numvotes = "matchedTitles.numVotes"
      val startYear = "startYear"
      val matchedTitles_startYear = "matchedTitles.startYear"
      val titleType = "titleType"
      val matchedTitles_titleType = "matchedTitles.titleType"

/*      private def docToJson: Pipe[F, Document, Json] = strDoc => {
        for {
          doc <- strDoc
          json <- Stream.eval(Sync[F].delay(parse(doc.toJson()) match {
            case Right(validJson) => validJson
            case Left(parseFailure) =>
              L.error(""""Parsing failure" exception={} """, parseFailure.toString)
              Json.Null
          }))
        } yield json
      }*/

      def getTitleModelFilters(title: String): F[Filter] = for {
        bsonCombo <- Sync[F].delay(text(title).and(regex(primaryTitle, title)))
      } yield bsonCombo

      implicit def convertBooleanToInt(b: Boolean): asInt = new asInt(b)

      private def mapQtype(qType: QueryObj, titleString: String, nameString: String): String =
        qType match {
          case TitleQuery => titleString
          case NameQuery  => nameString
        }

      private def inOrEq[T](fieldName: String, inputList: List[T]): Filter = inputList match {
        case manyElements:List[T] if manyElements.size > 1    => Filter.in(fieldName, manyElements)
        case singleElement:List[T] if singleElement.size == 1 => Filter.eq(fieldName, singleElement.head)
      }

      private def between(fieldname: String, valueRange: List[Int]): Filter = {
        Filter.gte(fieldname, valueRange.head).and(Filter.lte(fieldname, valueRange.last))
      }

      def getParamList(params: ReqParams, qType: QueryObj): F[Filter] = for {
        bList <- Sync[F].delay(
          List(
            params.year.map(yr => between(mapQtype(qType, startYear, matchedTitles_startYear), yr)),
            params.genre.map(genre => inOrEq(mapQtype(qType, genresList, matchedTitles_genresList), genre)),
            params.titleType.map(tt => inOrEq(mapQtype(qType, titleType, matchedTitles_titleType), tt)),
            params.isAdult.map(isAdlt => Filter.eq(mapQtype(qType, isAdult, matchedTitles_isAdult), isAdlt.toInt)),
            params.votes.map(v => Filter.gte(mapQtype(qType, numvotes, matchedTitles_numvotes), v))
          ).flatten)
        bList2 <- Sync[F].delay(bList
          .foldLeft(Filter.empty){ case (acc, filt) =>
            acc.and(filt)
          })
      } yield bList2

      def getParamModelFilters(params: ReqParams, qType: QueryObj): F[Filter] = getParamList(params, qType)

/*      def getDocKeyVal(bdoc: Document): (String, BsonValue) = {
        val bKey = bdoc.keys.head
        (bKey, bdoc.get(bKey))
      }*/

/*      def combineBson(bson: List[Bson]): F[Bson] = Sync[F].delay{
        bson.reduceLeft { (a: Bson, b: Bson) =>
            val tuple = getDocKeyVal(b.toBsonDocument)
            a.toBsonDocument.append(tuple._1, tuple._2)
        }
      }*/

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
      override def getByTitle2(optTitle: Option[String], rating: Double, params: ReqParams): Stream[F, TitleRec] = for {
        paramBson <- Stream.eval(getParamModelFilters(params, TitleQuery))
        ratingBson <- Stream.eval(Sync[F].delay(
            Filter.exists(averageRating).and(
              Filter.eq(averageRating, Decimal128.NaN)).or(
              Filter.notExists(averageRating)).or(
              Filter.gte(averageRating, rating))
        ))
        titleFilter <- Stream.eval(optTitle match {
          case Some(title) => getTitleModelFilters(title)
          case _           => Sync[F].delay(Filter.empty)
        })
        sortBson <- Stream.eval(Sync[F].delay(Document(startYear -> BsonValue.int(-1))))
        bsonFilter <- Stream.eval(Sync[F].delay(titleFilter.and(ratingBson).and(paramBson)))
        dbList <- Stream.eval(titleFx2.find(bsonFilter)
          .limit(DOCLIMIT)
          .skip(0)
          .sort(sortBson)
          .projection(Projection.exclude(genres))
          .stream
//          .through(docToJson)
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
/*      override def getByName(name: String, rating: Double, params: ReqParams): Stream[F, Json] = for {
        matchTitleWithName <- Stream.eval(
              combineBson(List(exists(knownForTitles, exists = true),
                mdne(knownForTitles, ""),
                mdbeq(primaryName, name)))
        )
        titleMatchFilter <- Stream.eval(Sync[F].delay(
          Aggregates.filter(matchTitleWithName)
        ))

        lookupFilter <- Stream.eval(Sync[F].delay(
          Aggregates.lookup(titleCollection,
            knownForTitlesList,
            "_id",
            matchedTitles)
        ))

        paramsList <- Stream.eval(getParamList(params, NameQuery))
        ratingBson <- Stream.eval(Sync[F].delay(
          or(
            mdbeq(matchedTitles_averageRating, Decimal128.NaN),
            gte(matchedTitles_averageRating, rating))
        )
        )
        bsonCondensedList <- Stream.eval(combineBson(List(paramsList, ratingBson)))
        matchLookupsFilter <- Stream.eval(Sync[F].delay(
          Aggregates.filter(bsonCondensedList)
        ))

        projectionsList <- Stream.eval(Sync[F].delay(
          ProjectionUtils.getProjectionFields(Map(
            matchedTitles_genres -> false
          ))
        ))
        projectionFilter <- Stream.eval(Sync[F].delay(
          Aggregates.project(ProjectionUtils.getProjections(projectionsList))
        ))

        unwindFilter <- Stream.eval(Sync[F].delay(
          Aggregates.unwind(s"$$$matchedTitles")
        ))

        groupFilter <- Stream.eval(Sync[F].delay(
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
          .stream
          .through(docToJson)
          .compile.toList)
        json <- Stream.emits(dbList)
      } yield json*/

      override def getByName2(name: String, rating: Double, params: ReqParams): Stream[F, TitleRec] = for {
        matchTitleWithName <- Stream.eval(Sync[F].delay(
          exists(knownForTitles).and(
            Filter.ne(knownForTitles, "")).and(
            Filter.eq(primaryName, name))
        ))
        titleMatchFilter <- Stream.eval(Sync[F].delay(
          Aggregate.matchBy(matchTitleWithName)
        ))

        lookupFilter <- Stream.eval(Sync[F].delay(
          Aggregate.lookup(titleCollection,
            knownForTitlesList,
            "_id",
            matchedTitles)
        ))

        paramsList <- Stream.eval(getParamList(params, NameQuery))
        ratingBson <- Stream.eval(Sync[F].delay(
            Filter.eq(matchedTitles_averageRating, Decimal128.NaN).or(
              Filter.gte(matchedTitles_averageRating, rating)))
        )
        bsonCondensedList <- Stream.eval(Sync[F].delay(paramsList.and(ratingBson)))
        matchLookupsFilter <- Stream.eval(Sync[F].delay(
          Aggregate.matchBy(bsonCondensedList)
        ))

        projectionsList <- Stream.eval(Sync[F].delay(
          ProjectionUtils.getProjectionFields(Map(
            matchedTitles_genres -> false
          ))
        ))
        projectionFilter <- Stream.eval(Sync[F].delay(
          Aggregate.project(ProjectionUtils.getProjections(projectionsList))
        ))

        unwindFilter <- Stream.eval(Sync[F].delay(
          Aggregate.unwind(s"$$$matchedTitles")
        ))

        groupFilter <- Stream.eval(Sync[F].delay(
          Aggregate.group("$_id",
            Accumulator.first(primaryName, s"$$$primaryName")
              .first(firstName, s"$$$firstName")
              .first(lastName, s"$$$lastName")
              .first(birthYear, s"$$$birthYear")
              .first(deathYear, s"$$$deathYear")
              .push(matchedTitles, s"$$$matchedTitles")
          )
        ))

        aggregation <- Stream.eval(Sync[F].delay(
          Seq(
            titleMatchFilter,
            lookupFilter,
            projectionFilter,
            unwindFilter,
            matchLookupsFilter,
            groupFilter
          ).reduce{ _ combinedWith _}
        ))
        dbList <- Stream.eval(nameFx2.aggregate[TitleRec](aggregation)
          .stream
//          .through(docToJson)
          .compile.toList)
        json <- Stream.emits(dbList)
      } yield json

      /**
       * {
       *       $match: {
       *                primaryName: 'June Lockhart',
       *                category: { $in: [ 'actor', 'actress' ] }
       *              }
       *  }, {
       *       $lookup: {
       *                 from: 'title_basics_ratings',
       *                 localField: 'tconst',
       *                 foreignField: '_id',
       *                 as: 'matchedTitles'
       *                }
       *  }, {
       *       $match: {
       *                'matchedTitles.titleType': 'movie',
       *                'matchedTitles.genresList': 'Drama',
       *                'matchedTitles.averageRating': { $gte: 5 }
       *               }
       *  }, {
       *       $unwind: {
       *                  path: '$matchedTitles'
       *                }
       *  }, {
       *       $group: {
       *                 _id: '$_id',
       *                 matchedTitles: { $push: '$matchedTitles' }
       *               }
       *  }, {
       *       $unwind: {
       *                  path: '$matchedTitles'
       *                }
       *  }, {
       *       $replaceRoot: {
       *                       newRoot: '$matchedTitles'
       *                     }
       *  }
       *
       * @param name of actor
       * @param rating IMDB
       * @param params body
       * @return
       */
/*      override def getByEnhancedName(name: String, rating: Double, params: ReqParams): Stream[F, Json] = for {
        matchNameAndRole <- Stream.eval(
          combineBson(List(
            mdbeq(primaryName, name),
            in(category, roleList:_*)
          )
        ))
        nameMatchFilter <- Stream.eval(Sync[F].delay(
          Aggregates.filter(matchNameAndRole)
        ))

        lookupFilter <- Stream.eval(Sync[F].delay(
          Aggregates.lookup(titleCollection,
            tconst,
            "_id",
            matchedTitles)
        ))

        paramsList <- Stream.eval(getParamList(params, NameQuery))
        ratingBson <- Stream.eval(Sync[F].delay(
          or(
            mdbeq(matchedTitles_averageRating, Decimal128.NaN),
            gte(matchedTitles_averageRating, rating))
        )
        )
        bsonCondensedList <- Stream.eval(Sync[F].delay{
          Document(paramsList.toBsonDocument.entrySet.asScala.toList.map{tuple => tuple.getKey -> tuple.getValue }.toMap ++
            ratingBson.toBsonDocument.entrySet.asScala.toList.map{tuple => tuple.getKey -> tuple.getValue}.toMap)
        })
        matchLookupsFilter <- Stream.eval(Sync[F].delay(
          Aggregates.filter(bsonCondensedList)
        ))

        unwindFilter <- Stream.eval(Sync[F].delay(
          Aggregates.unwind(s"$$$matchedTitles")
        ))

        groupFilter <- Stream.eval(Sync[F].delay(
          Aggregates.group("$_id",
            Accumulators.push(matchedTitles, s"$$$matchedTitles")
          )
        ))

        replaceFilter <- Stream.eval(Sync[F].delay(
          replaceRoot(s"""$$$matchedTitles""")
        ))

        dbList <- Stream.eval(titlePrincipalsFx.aggregate(
          Seq(
            nameMatchFilter,
            lookupFilter,
            matchLookupsFilter,
            unwindFilter,
            groupFilter,
            unwindFilter,
            replaceFilter,
          )
        )
          .stream
          .through(docToJson)
          .compile.toList)
        json <- Stream.emits(dbList)
      } yield json*/

      override def getByEnhancedName2(name: String, rating: Double, params: ReqParams): Stream[F, TitleRec] = for {
        matchNameAndRole <- Stream.eval(Sync[F].delay(
            Filter.eq(primaryName, name).and(
            Filter.in(category, roleList))
          ))
        nameMatchFilter <- Stream.eval(Sync[F].delay(
          Aggregate.matchBy(matchNameAndRole)
        ))

        lookupFilter <- Stream.eval(Sync[F].delay(
          Aggregate.lookup(titleCollection,
            tconst,
            "_id",
            matchedTitles)
        ))

        paramsList <- Stream.eval(getParamList(params, NameQuery))
        ratingBson <- Stream.eval(Sync[F].delay(
            Filter.eq(matchedTitles_averageRating, Decimal128.NaN).or(
            Filter.gte(matchedTitles_averageRating, rating)))
        )
        bsonCondensedList <- Stream.eval(Sync[F].delay{
          paramsList.and(ratingBson)
        })
        matchLookupsFilter <- Stream.eval(Sync[F].delay(
          Aggregate.matchBy(bsonCondensedList)
        ))

        unwindFilter <- Stream.eval(Sync[F].delay(
          Aggregate.unwind(s"$$$matchedTitles")
        ))

        groupFilter <- Stream.eval(Sync[F].delay(
          Aggregate.group("$_id",
            Accumulator.push(matchedTitles, s"$$$matchedTitles")
          )
        ))

        replaceFilter <- Stream.eval(Sync[F].delay(
          Aggregate.replaceWith(s"""$$$matchedTitles""")
        ))

        aggregation <- Stream.eval(Sync[F].delay(
          Seq(
            nameMatchFilter,
            lookupFilter,
            matchLookupsFilter,
            unwindFilter,
            groupFilter,
            unwindFilter,
            replaceFilter,
          ).reduce(_ combinedWith _)
        ))

        dbList <- Stream.eval(titlePrincipalsFx2.aggregate/*WithCodec*/[TitleRec](aggregation)
          .stream
//          .through(docToJson)
          .compile.toList)
        json <- Stream.emits(dbList)
      } yield json

      /**
       * {
       *   $match: {
       *            lastName: { $regex: /^Cra^/ },
       *            firstName: 'Daniel'
       *           }
       *   }, {
       *   $project: {
       *               firstName: 1,
       *               lastName: 1
       *             }
       *   }, {
       *   $group: {
       *              _id: "$lastName",
       *              firstName: { $first: "$firstName" }
       *           }
       *   }, {
       *   $sort: {
       *             firstName: 1,
       *             _id: 1
       *          }
       *   }, {
       *   $project: {
       *               _id: 0,
       *               firstName: 1,
       *               lastName: 1
       *             }
       *   }, {
       *   $limit: 20
       *   }
       * }
       * @param namePrefix first' '[lastname]
       * @return list
       */
/*      override def getAutosuggestName(namePrefix: String): Stream[F, Json] = for {
        names <- Stream.eval(Sync[F].delay(
          if (namePrefix contains " ")
            namePrefix.split(" ").toList
          else
            List(namePrefix)
        ))

        lastFirst <- Stream.eval(
          if (names.size == 1)
            Sync[F].delay(regex(lastName, s"""^${names.head}"""))
          else
            combineBson(List(
              regex(lastName, s"""^${names.last}"""),
              mdbeq(firstName, names.head)
            ))
        )

        nameMatchFilter <- Stream.eval(Sync[F].delay(
          Aggregates.filter(lastFirst)
        ))

        projectionsList <- Stream.eval(Sync[F].delay(
          ProjectionUtils.getProjectionFields(Map(
            firstName -> true,
            lastName -> true
          ))
        ))

        projectionFilter <- Stream.eval(Sync[F].delay(
          Aggregates.project(ProjectionUtils.getProjections(projectionsList))
        ))

        groupFilter <- Stream.eval(Sync[F].delay(
          Aggregates.group("$lastName",
            Accumulators.first(firstName, s"$$$firstName"))
        ))

        sortFilter <- Stream.eval(Sync[F].delay(
          Aggregates.sort(Document(
            id -> BsonNumber(1),
            firstName -> BsonNumber(1)))
        ))

        lastProjectionsList <- Stream.eval(Sync[F].delay(
          ProjectionUtils.getProjectionFields(Map(
            id -> false,
            firstName -> true,
          )) ++ List(BsonDocument((lastName, BsonString("$_id"))))
        ))

        lastProjectFilter <- Stream.eval(Sync[F].delay(
          Aggregates.project(ProjectionUtils.getProjections(lastProjectionsList))
        ))

        limitFilter <- Stream.eval(Sync[F].delay(
          Aggregates.limit(AUTOSUGGESTLIMIT)
        ))

        dbList <- Stream.eval(nameFx.aggregate(
          Seq(
            nameMatchFilter,
            projectionFilter,
            groupFilter,
            sortFilter,
            lastProjectFilter,
            limitFilter
          )
        )
          .stream
          .through(docToJson)
          .compile.toList)
        json <- Stream.emits(dbList)
      } yield json*/

      override def getAutosuggestName2(namePrefix: String): Stream[F, AutoNameRec] = for {
        names <- Stream.eval(Sync[F].delay(
          if (namePrefix contains " ")
            namePrefix.split(" ").toList
          else
            List(namePrefix)
        ))

        lastFirst <- Stream.eval(
          if (names.size == 1)
            Sync[F].delay(regex(lastName, s"""^${names.head}"""))
          else
            Sync[F].delay(Filter.regex(lastName, s"""^${names.last}""").and(
              Filter.eq(firstName, names.head)))
        )

        nameMatchFilter <- Stream.eval(Sync[F].delay(
          Aggregate.matchBy(lastFirst)
        ))

        projectionsList <- Stream.eval(Sync[F].delay(
          ProjectionUtils.getProjectionFields(Map(
            firstName -> true,
            lastName -> true
          ))
        ))

        projectionFilter <- Stream.eval(Sync[F].delay(
          Aggregate.project(ProjectionUtils.getProjections(projectionsList))
        ))

        groupFilter <- Stream.eval(Sync[F].delay(
          Aggregate.group("$lastName",
            Accumulator.first(firstName, s"$$$firstName"))
        ))

        sortFilter <- Stream.eval(Sync[F].delay(
          Aggregate.sort(Sort.asc(id, firstName))
        ))

        lastProjectionsList <- Stream.eval(Sync[F].delay(
          ProjectionUtils.getProjectionFields(Map(
            id -> false,
            firstName -> true,
          )) ++ List(Projection.computed(lastName, "$_id"))
        ))

        lastProjectFilter <- Stream.eval(Sync[F].delay(
          Aggregate.project(ProjectionUtils.getProjections(lastProjectionsList))
        ))

        limitFilter <- Stream.eval(Sync[F].delay(
          Aggregate.limit(AUTOSUGGESTLIMIT)
        ))

        aggregation <- Stream.eval(Sync[F].delay(
          Seq(
            nameMatchFilter,
            projectionFilter,
            groupFilter,
            sortFilter,
            lastProjectFilter,
            limitFilter
          ).reduce(_ combinedWith _)
        ))

        dbList <- Stream.eval(nameFx.aggregate/*WithCodec*/[AutoNameRec](aggregation)
          .stream
//          .through(docToJson)
          .compile.toList)
        json <- Stream.emits(dbList)
      } yield json

      /**
       * {
       *   $match: {
       *              $and: [
       *                      {$text: {$search: 'Gone with the W'}},
       *                      {'primaryTitle': {$regex: /^Gone with the W/}}
       *              ]
       *           }
       *  }, {
       *   $project: {
       *               _id: 0,
       *               'primaryTitle' : 1
       *             }
       *  }, {
       *    $group: {
       *               _id: '$primaryTitle',
       *               titles: { $first: "$primaryTitle" }
       *            }
       *  }, {
       *     $sort: {
       *               titles: 1
       *            }
       *  }, {
       *     $limit: 20
       *  }
       * @param titlePrefix substring with whitespace
       * @return sorted, distinct titles
       */
/*      override def getAutosuggestTitle(titlePrefix: String): Stream[F, Json] = for {
        titleTextRegex <- Stream.eval(
          combineBson(List(
            text(titlePrefix),
            regex(primaryTitle, s"""^$titlePrefix""")
          ))
        )

        titleMatchFilter <- Stream.eval(Sync[F].delay(
          Aggregates.filter(titleTextRegex)
        ))

        projectionsList <- Stream.eval(Sync[F].delay(
          ProjectionUtils.getProjectionFields(Map(
            id -> false,
            primaryTitle -> true
          ))
        ))

        projectionFilter <- Stream.eval(Sync[F].delay(
          Aggregates.project(ProjectionUtils.getProjections(projectionsList))
        ))

        groupFilter <- Stream.eval(Sync[F].delay(
          Aggregates.group("$primaryTitle",
            Accumulators.first(primaryTitle, s"$$$primaryTitle"))
        ))

        sortFilter <- Stream.eval(Sync[F].delay(
          Aggregates.sort(Document(primaryTitle -> BsonNumber(1)))
        ))

        limitFilter <- Stream.eval(Sync[F].delay(
          Aggregates.limit(AUTOSUGGESTLIMIT)
        ))

        dbList <- Stream.eval(titleFx.aggregate(
          Seq(
            titleMatchFilter,
            projectionFilter,
            groupFilter,
            sortFilter,
            limitFilter,
            projectionFilter
          )
        )
          .stream
          .through(docToJson)
          .compile.toList)
        json <- Stream.emits(dbList)
      } yield json*/

      override def getAutosuggestTitle2(titlePrefix: String): Stream[F, AutoTitleRec] = for {
        titleTextRegex <- Stream.eval(
          Sync[F].delay(Filter.text(titlePrefix).and(
            Filter.regex(primaryTitle, s"""^$titlePrefix""")))
        )

        titleMatchFilter <- Stream.eval(Sync[F].delay(
          Aggregate.matchBy(titleTextRegex)
        ))

        projectionsList <- Stream.eval(Sync[F].delay(
          ProjectionUtils.getProjectionFields(Map(
            id -> false,
            primaryTitle -> true
          ))
        ))

        projectionFilter <- Stream.eval(Sync[F].delay(
          Aggregate.project(ProjectionUtils.getProjections(projectionsList))
        ))

        groupFilter <- Stream.eval(Sync[F].delay(
          Aggregate.group("$primaryTitle",
            Accumulator.first(primaryTitle, s"$$$primaryTitle"))
        ))

        sortFilter <- Stream.eval(Sync[F].delay(
          Aggregate.sort(Sort.asc(primaryTitle))
        ))

        limitFilter <- Stream.eval(Sync[F].delay(
          Aggregate.limit(AUTOSUGGESTLIMIT)
        ))

        aggregation <- Stream.eval(Sync[F].delay(
          Seq(
            titleMatchFilter,
            projectionFilter,
            groupFilter,
            sortFilter,
            limitFilter,
            projectionFilter
          ).reduce(_ combinedWith _)
        ))

        dbList <- Stream.eval(titleFx.aggregate/*WithCodec*/[AutoTitleRec](aggregation)
          .stream
//          .through(docToJson)
          .compile.toList)
        json <- Stream.emits(dbList)
      } yield json
    }
}
