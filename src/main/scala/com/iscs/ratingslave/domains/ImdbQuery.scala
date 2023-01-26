package com.iscs.ratingslave.domains

import cats.effect.Sync
import cats.effect.kernel.Clock
import com.iscs.ratingslave.util.ProjectionUtils
import mongo4cats.collection.MongoCollection
import cats.implicits._
import com.iscs.ratingslave.domains.ImdbQuery.{AutoNameRec, AutoTitleRec, NameTitleRec, TitleRec}
import com.iscs.ratingslave.model.Requests.ReqParams
import com.iscs.ratingslave.util.asInt
import com.typesafe.scalalogging.Logger
import fs2.Stream
import io.circe.generic.auto._
import mongo4cats.circe._
import mongo4cats.operations.{Accumulator, Aggregate, Filter, Projection, Sort}
import mongo4cats.operations.Filter.{regex, text}
import org.bson.types.Decimal128

import scala.language.implicitConversions

abstract class QueryObj
case object TitleQuery extends QueryObj
case object NameQuery extends QueryObj

trait ImdbQuery[F[_]] {
  def getByTitle(title: Option[String], rating: Double, params: ReqParams): Stream[F, TitleRec]
  def getByName(name: String, rating: Double, params: ReqParams): Stream[F, NameTitleRec]
  def getByEnhancedName(name: String, rating: Double, params: ReqParams): Stream[F, TitleRec]
  def getAutosuggestTitle(titlePrefix: String): Stream[F, AutoTitleRec]
  def getAutosuggestName(titlePrefix: String): Stream[F, AutoNameRec]
}

object ImdbQuery {
  private val L = Logger[this.type]

  private val DOCLIMIT = 200
  private val AUTOSUGGESTLIMIT = 20

  def apply[F[_]](implicit ev: ImdbQuery[F]): ImdbQuery[F] = ev

  final case class AutoNameRec(firstName: String, lastName: Option[String])

  final case class AutoTitleRec(primaryTitle: Option[String])

  final case class TitleRec(averageRating: Option[Double], numVotes: Option[Int],
                            titleType: String, primaryTitle: String, originalTitle: String,
                            isAdult: Int, startYear: Int, endYear: String, runTimeMinutes: Option[Int],
                            genresList: List[String])

  final case class NameTitleRec(primaryName: String, firstName: String, lastName: String, birthYear: Int,
                                deathYear1: Option[String] = None,
                                matchedTitles: List[TitleRec])

  def impl[F[_]: Sync](titleFx: MongoCollection[F, TitleRec],
                       titlePrincipalsFx: MongoCollection[F, TitleRec],
                       nameFx: MongoCollection[F, AutoNameRec]): ImdbQuery[F] =
    new ImdbQuery[F] {
      val titleCollection = "title_basics_ratings"

      val id = "_id"
      val birthYear = "birthYear"
      val deathYear = "deathYear1"
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
        bsonFilter <- Stream.eval(Sync[F].delay(titleFilter.and(ratingBson).and(paramBson)))
        (byTitleTime, dbList) <- Stream.eval(Clock[F].timed(titleFx.find(bsonFilter)
          .limit(DOCLIMIT)
          .skip(0)
          .sortByDesc(startYear)
          .projection(Projection.exclude(genres))
          .stream
          .compile.toList))
        _ <- Stream.eval(Sync[F].delay(L.info(s"getByTitle {} ms", byTitleTime.toMillis)))
        json <- Stream.emits(dbList)
      } yield json

      /**
       * ('name_basics').aggregate(
       * [
       *   {"$match": {"$and": [{"$and": [{"knownForTitles": {"$exists": true}}, {"knownForTitles": {"$ne": ""}}]}, {"primaryName": "Steve McQueen"}]}},
       *   {"$lookup": {"from": "title_basics_ratings", "localField": "knownForTitlesList", "foreignField": "_id", "as": "matchedTitles"}},
       *   {"$project": {"matchedTitles.genres": 0, "deathYear1": 0}},
       *   {"$unwind": {"path": "$matchedTitles"}},
       *   {"$match": {"$and": [{"$and": [{"$and": [{"$and": [{}, {"$and": [{"matchedTitles.startYear": {"$gte": 2010}}, {"matchedTitles.startYear": {"$lte": 2018}}]}]}, {"matchedTitles.genresList": {"$in": ["Action", "Drama", "Horror"]}}]}, {"matchedTitles.titleType": "movie"}]}, {"$or": [{"matchedTitles.averageRating": {"$numberDecimal": "NaN"}}, {"matchedTitles.averageRating": {"$gte": 4.0}}]}]}},
       *   {"$group": {"_id": "$_id",
       *               "primaryName": {"$first": "$primaryName"},
       *               "firstName": {"$first": "$firstName"},
       *               "lastName": {"$first": "$lastName"},
       *               "birthYear": {"$first": "$birthYear"},
       *               "deathYear": {"$first": "$deathYear"},
       *                "matchedTitles": {
                                           "$push": "$matchedTitles"
                                         }
                       }}
       * ])
       * @param name first lastname
       * @param rating IMDB
       * @param params object
       * @return stream of object
       */
      override def getByName(name: String, rating: Double, params: ReqParams): Stream[F, NameTitleRec] = for {
        matchTitleWithName <- Stream.eval(Sync[F].delay(
          Filter.exists(knownForTitles).and(
            Filter.ne(knownForTitles, "")).and(
            Filter.eq(primaryName, name))
        ))

        paramsList <- Stream.eval(getParamList(params, NameQuery))
        ratingBson <- Stream.eval(Sync[F].delay(
          Filter.eq(matchedTitles_averageRating, Decimal128.NaN).or(
            Filter.gte(matchedTitles_averageRating, rating))))
        bsonCondensedList <- Stream.eval(Sync[F].delay(paramsList.and(ratingBson)))

        projectionsList <- Stream.eval(Sync[F].delay(
          ProjectionUtils.getProjectionFields(Map(
            matchedTitles_genres -> false,
            deathYear -> false
          ))
        ))

        comboagg <- Stream.eval(Sync[F].delay(
          Aggregate.matchBy(matchTitleWithName)
            .lookup(titleCollection,
              knownForTitlesList,
              "_id",
              matchedTitles)
            .project(ProjectionUtils.getProjections(projectionsList))
            .unwind(s"$$$matchedTitles")
            .matchBy(bsonCondensedList)
            .group("$_id",
              Accumulator.first(primaryName, s"$$$primaryName")
                .first(firstName, s"$$$firstName")
                .first(lastName, s"$$$lastName")
                .first(birthYear, s"$$$birthYear")
                .first(deathYear, s"$$$deathYear")
                .push(matchedTitles, s"$$$matchedTitles")
            )
        ))
        (byNameTime, dbList) <- Stream.eval(Clock[F].timed(
          nameFx.aggregateWithCodec[NameTitleRec](comboagg)
            .stream
            .compile.toList))
        _ <- Stream.eval(Sync[F].delay(L.info(s"getByName {} ms", byNameTime.toMillis)))
        json <- Stream.emits(dbList)
      } yield json

      /**
       *  ('name_basics').aggregate([
       *     {"$match": {"$and": [{"primaryName": "June Lockhart"}, {"category": {"$in": ["actor", "actress"]}}]}},
       *     {"$lookup": {"from": "title_basics_ratings", "localField": "tconst", "foreignField": "_id", "as": "matchedTitles"}},
       *     {"$match": {"$and": [
       *                           {"$and": [
       *                                      {"$and": [
       *                                                 {"$and": [
       *                                                            {"$and": [
       *                                                                       {},
       *                                                                       {"$and": [
       *                                                                                  {"matchedTitles.startYear": {"$gte": 1937}},
       *                                                                                  {"matchedTitles.startYear": {"$lte": 1979}}
       *                                                                                ]}
       *                                                                     ]},
       *                                                            {"matchedTitles.genresList": "Comedy"}
       *                                                          ]},
       *                                                  {"matchedTitles.isAdult": 0}
       *                                               ]},
       *                                        {"matchedTitles.numVotes": {"$gte": 0}}
       *                                     ]},
       *                            {"$or": [
       *                                       {"matchedTitles.averageRating": {"$numberDecimal": "NaN"}},
       *                                       {"matchedTitles.averageRating": {"$gte": 4.0}}
       *                                    ]}
       *                          ]}},
       *     {"$unwind": {"path": "$matchedTitles"}},
       *     {"$group": {"_id": "$_id", "matchedTitles": {"$push": "$matchedTitles"}}},
       *     {"$unwind": {"path": "$matchedTitles"}},
       *     {"$replaceWith": "$matchedTitles"}]
       *  ])
       * @param name first lastname
       * @param rating IMDB
       * @param params object
       * @return stream object
       */
      override def getByEnhancedName(name: String, rating: Double, params: ReqParams): Stream[F, TitleRec] = for {
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

        (byEnhancedNameTime, dbList) <- Stream.eval(Clock[F].timed(
          titlePrincipalsFx.aggregateWithCodec[TitleRec](aggregation)
            .stream
            .compile.toList))
        _ <- Stream.eval(Sync[F].delay(L.info(s"getByEnhancedName {} ms", byEnhancedNameTime.toMillis)))
        json <- Stream.emits(dbList)
      } yield json

      /**
       * ('name_basics').aggregate([
       *   {"$match": {"$and": [{"lastName": "/%Caret%Cra"}, {"firstName": "Daniel"}]}},
       *   {"$project": {"firstName": 1, "lastName": 1}},
       *   {"$group": {"_id": "$lastName",
       *               "firstName": {"$first": "$firstName"}}},
       *               {"$sort": {"_id": 1, "firstName": 1}},
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

        (autosuggestTime, dbList) <- Stream.eval(Clock[F].timed(
          nameFx.aggregateWithCodec[AutoNameRec](aggregation)
            .stream
            .compile.toList))
        _ <- Stream.eval(Sync[F].delay(L.info(s"getAutosuggestName {} ms", autosuggestTime.toMillis)))
        json <- Stream.emits(dbList)
      } yield json

      /**
       * ('name_basics').aggregate([
       *     {"$match": {"$and": [{"$text": {"$search": "Gone with the W"}}, {"primaryTitle": /%caret%Gone with the W/}]}},
       *     {"$project": {"_id": 0, "primaryTitle": 1}},
       *     {"$group": {"_id": "$primaryTitle", "primaryTitle": {"$first": "$primaryTitle"}}}, {"$sort": {"primaryTitle": 1}},
       *     {"$limit": 20},
       *     {"$project": {"_id": 0, "primaryTitle": 1}}
       * ])
       * @param titlePrefix title regex
       * @return stream of object
       */
      override def getAutosuggestTitle(titlePrefix: String): Stream[F, AutoTitleRec] = for {
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

        (autosuggestTitleTime, dbList) <- Stream.eval(Clock[F].timed(
          titleFx.aggregateWithCodec[AutoTitleRec](aggregation)
            .stream
            .compile.toList))
        _ <- Stream.eval(Sync[F].delay(L.info(s"getAutosuggestTitle {} ms", autosuggestTitleTime.toMillis)))
        json <- Stream.emits(dbList)
      } yield json
    }
}
