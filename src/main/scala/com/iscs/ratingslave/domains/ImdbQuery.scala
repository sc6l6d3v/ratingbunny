package com.iscs.ratingslave.domains

import cats.effect._
import cats.effect.implicits._
import cats.effect.kernel.Clock
import cats.implicits._
import com.iscs.ratingslave.domains.ImdbQuery.{AutoNameRec, AutoTitleRec, TitleRec, TitleRecPath}
import com.iscs.ratingslave.model.Requests.ReqParams
import com.iscs.ratingslave.util.DecodeUtils
import com.typesafe.scalalogging.Logger
import fs2.Stream
import io.circe.generic.auto._
import mongo4cats.circe._
import mongo4cats.collection.MongoCollection
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.{Method, Request, Uri}

import scala.language.implicitConversions

trait ImdbQuery[F[_]] {
  def getByTitle(title: Option[String], rating: Double, params: ReqParams, limit: Int): Stream[F, TitleRec]
  def getByTitlePath(title: Option[String], rating: Double, params: ReqParams): Stream[F, TitleRecPath]
  def getByName(name: String, rating: Double, params: ReqParams): Stream[F, TitleRec]
  def getByEnhancedName(name: String, rating: Double, params: ReqParams): Stream[F, TitleRec]
  def getAutosuggestTitle(titlePrefix: String, rating: Double, params: ReqParams): Stream[F, AutoTitleRec]
  def getAutosuggestName(titlePrefix: String, rating: Double, params: ReqParams): Stream[F, AutoNameRec]
}

object ImdbQuery extends DecodeUtils {
  private val L = Logger[this.type]

  def apply[F[_]](implicit ev: ImdbQuery[F]): ImdbQuery[F] = ev

  final case class AutoNameRec(firstName: String, lastName: Option[String])

  final case class AutoTitleRec(primaryTitle: Option[String])

  private final case class PathRec(path: String)

  final case class TitleRec(_id: String, averageRating: Option[Double], numVotes: Option[Int],
                            titleType: String, primaryTitle: String, originalTitle: String,
                            isAdult: Int, startYear: Int, endYear: String, runtimeMinutes: Option[Int],
                            genresList: Option[List[String]])

  final case class TitleRecPath(_id: String, averageRating: Option[Double], numVotes: Option[Int],
                            titleType: String, primaryTitle: String, originalTitle: String,
                            isAdult: Int, startYear: Int, endYear: String, runtimeMinutes: Option[Int],
                            genresList: List[String], posterPath: Option[String])

  def impl[F[_]: Async](compFx: MongoCollection[F, TitleRec],
                        imageHost: String,
                        client: Client[F]): ImdbQuery[F] =
    new ImdbQuery[F] with QuerySetup  {
      private val proto = protoc(imageHost)
      private val imagePath = s"$proto://$imageHost/path"
      L.info(s"imageHost {} metaImage {}", imageHost, imagePath)

      val chunkSize = 256

      def getTitlePathsStream(titleRecsStr: Stream[F, TitleRecPath], chunkSize: Int): Stream[F, TitleRecPath] = {
        titleRecsStr
          .chunkN(chunkSize, allowFewer = true) // Chunk the stream into groups of chunkSize
          .flatMap { chunk =>
            // Convert the chunk to a list, process each element in parallel, and flatten back into the stream
            Stream.eval(chunk.toList.parTraverse { titleRec => // Evaluate parallel traversal for the chunk
              for {
                pathRec <- getPath(titleRec._id) // Assume getPath returns F[PathRec] with a 'path' member
              } yield titleRec.copy(posterPath = Some(pathRec.path))
            }).flatMap(Stream.emits) // Directly emit the results as a stream
          }
      }

      /**
       * db.title_principals_namerating.aggregate([
       *    { $match: {
       *            primaryTitle: { $regex: "Gone " },   or primaryTitle: "Gone with the Light", if exact
       *            startYear: { "$gte": 2019, "$lte": 2023 },
       *            genresList: { "$in": ["Adventure"] },
       *            titleType: "tvEpisode",
       *            isAdult: 0,
       *            numVotes: { "$gte": 50 },
       *            $or: [{ "averageRating": { "$exists": false } },
       *                  { "averageRating": { "$gte": 7.0 } }]
       *          }
       * }, { $group: {
       *               _id: "$tconst",
       *               averageRating: { $first: "$averageRating" },
       *               numVotes: { $first: "$numVotes" },
       *               titleType: { $first: "$titleType" },
       *               primaryTitle: { $first: "$primaryTitle" },
       *               originalTitle: { $first: "$originalTitle" },
       *               isAdult: { $first: "$isAdult" },
       *               startYear: { $first: "$startYear" },
       *               endYear: { $first: "$endYear" },
       *               runtimeMinutes: { $first: "$runtimeMinutes" },
       *               genresList: { $first: "$genresList" }
       *              }
       * }, { $project: {
       *                  averageRating: { $ifNull: ["$averageRating", "$$REMOVE"]},
       *                  numVotes: { $ifNull: ["$numVotes", "$$REMOVE"]},
       *                  titleType: 1,
       *                  primaryTitle: 1,
       *                  originalTitle: { $ifNull: ["$originalTitle", "$$REMOVE"]},
       *                  isAdult: 1,
       *                  startYear: 1,
       *                  endYear: 1,
       *                  runtimeMinutes: { $ifNull: ["$runtimeMinutes", "$$REMOVE"]},
       *                  genresList: 1,
       *               }
       * }, { $sort: { startYear: -1}}
       * ])
       * @param optTitle optional query param
       * @param rating   required numeric
       * @param params   other params
       * @return
       */
      override def getByTitle(optTitle: Option[String], rating: Double, params: ReqParams, limit: Int): Stream[F, TitleRec] = {
        val queryPipeline = genQueryPipeline(genTitleFilter(optTitle, rating, params), isLimited = true, limit)

        for {
          start <- Stream.eval(Clock[F].monotonic)
          midRef <- Stream.eval(Ref.of[F, Long](0L))
          resultStream <- compFx.aggregateWithCodec[TitleRec](queryPipeline).stream
            .evalMap { result =>
              Clock[F].monotonic.flatMap { mid =>
                midRef.set(mid.toMillis) >> Sync[F].pure(result)
              }
            }
            .onFinalize {
              for {
                end <- Clock[F].monotonic
                mid <- midRef.get
                _ <- Sync[F].delay(L.info(s"getByTitle took ${end.toMillis - start.toMillis} ms, aggregation took ${mid - start.toMillis} ms"))
              } yield ()
            }
        } yield resultStream
      }

      /**
       * db.getCollection("title_principals_namerating").aggregate([
       * {
       * $match: {
       * "startYear": { "$gte": 1961, "$lte": 2023 },
       * "genresList": { "$in": ["Thriller"] },
       * "titleType": "movie",
       * "isAdult": 0,
       * "numVotes": { "$gte": 500 },
       * "$or": [{ "averageRating": { "$exists": false } },
       * { "averageRating": { "$gte": 5.0 } }],
       * primaryName: "Steve McQueen"
       * }
       * },
       * {
       * $group: {
       * _id: "$tconst",
       * averageRating: { $first: "$averageRating" },
       * numVotes: { $first: "$numVotes" },
       * titleType: { $first: "$titleType" },
       * primaryTitle: { $first: "$primaryTitle" },
       * originalTitle: { $first: "$originalTitle" },
       * isAdult: { $first: "$isAdult" },
       * startYear: { $first: "$startYear" },
       * endYear: { $first: "$endYear" },
       * runtimeMinutes: { $first: "$runtimeMinutes" },
       * genresList: { $first: "$genresList" }
       * }
       * },
       * {
       * $project: {
       * averageRating: { $ifNull: ["$averageRating", "$$REMOVE"] },
       * numVotes: { $ifNull: ["$numVotes", "$$REMOVE"] },
       * titleType: 1,
       * primaryTitle: 1,
       * originalTitle: { $ifNull: ["$originalTitle", "$$REMOVE"] },
       * isAdult: 1,
       * startYear: 1,
       * endYear: 1,
       * runtimeMinutes: { $ifNull: ["$runtimeMinutes", "$$REMOVE"] },
       * genresList: 1,
       * }
       * },
       * {
       * $sort: {startYear: -1, numVotes: -1}
       * }
       * ]);
       *
       *
       * @param name   first lastname
       * @param rating IMDB
       * @param params object
       * @return stream of object
       */
      override def getByName(name: String, rating: Double, params: ReqParams): Stream[F, TitleRec] = {
        val queryPipeline = genQueryPipeline(genNameFilter(name, rating, params))

        Stream.eval(Clock[F].monotonic).flatMap { start =>
          compFx.aggregateWithCodec[TitleRec](queryPipeline).stream
            .onFinalize { // Ensure that logging operation happens once after all stream processing is complete
              for {
                end <- Clock[F].monotonic
                _ <- Sync[F].delay(L.info(s"getByName took {} ms", (end - start).toMillis))
              } yield ()
            }
        }
      }

      /**
       * db.getCollection("title_principals_namerating").aggregate([
       * {
       * $match: {
       * firstName: "Wallis",
       * lastName: "Day",
       * "startYear": { "$gte": 2010, "$lte": 2024 },
       * "genresList": { "$in": ["Adventure"] },
       * "titleType": "tvEpisode",
       * "isAdult": 0,
       * "numVotes": { "$gte": 500 },
       * "$or": [{ "averageRating": { "$exists": false } },
       * { "averageRating": { "$gte": 5.0 } }],
       * }
       * },
       * {
       * $group: {
       * _id: "$tconst",  // Group by tconst
       * averageRating: { $first: "$averageRating" },
       * numVotes: { $first: "$numVotes" },
       * titleType: { $first: "$titleType" },
       * primaryTitle: { $first: "$primaryTitle" },
       * originalTitle: { $first: "$originalTitle" },
       * isAdult: { $first: "$isAdult" },
       * startYear: { $first: "$startYear" },
       * endYear: { $first: "$endYear" },
       * runtimeMinutes: { $first: "$runtimeMinutes" },
       * genresList: { $first: "$genresList" }
       * }
       * },
       * {
       * $project: {
       * averageRating: { $ifNull: ["$averageRating", "$$REMOVE"] },
       * numVotes: { $ifNull: ["$numVotes", "$$REMOVE"] },
       * titleType: 1,
       * primaryTitle: 1,
       * originalTitle: { $ifNull: ["$originalTitle", "$$REMOVE"] },
       * isAdult: 1,
       * startYear: 1,
       * endYear: 1,
       * runtimeMinutes: { $ifNull: ["$runtimeMinutes", "$$REMOVE"] },
       * genresList: 1,
       * }
       * },
       * {
       * $sort: { startYear: -1, numVotes: -1}
       * }
       * ])
       * @param name   first lastname
       * @param rating IMDB
       * @param params object
       * @return stream object
       */
      override def getByEnhancedName(name: String, rating: Double, params: ReqParams): Stream[F, TitleRec] = {
        val queryPipeline = genQueryPipeline(genNameFilter(name, rating, params))

        Stream.eval(Clock[F].monotonic).flatMap { start =>
          compFx.aggregateWithCodec[TitleRec](queryPipeline).stream
            .onFinalize { // Ensure that logging operation happens once after all stream processing is complete
              for {
                end <- Clock[F].monotonic
                _ <- Sync[F].delay(L.info(s"getByEnhancedName took {} ms", (end - start).toMillis))
              } yield ()
            }
        }
      }

      /**
       * db.getCollection("title_principals_namerating").aggregate([
       * {
       * $match: {
       * firstName: { $regex: "^Dan"},
       * lastName: { $regex: "^Cra" },
       * "startYear": { "$gte": 1995, "$lte": 2024 },
       * "genresList": { "$in": ["Adventure"] },
       * "titleType": "movie",
       * "isAdult": 0,
       * "numVotes": { "$gte": 500 },
       * "$or": [{ "averageRating": { "$exists": false } },
       * { "averageRating": { "$gte": 4.0 } }]
       * }
       * },
       * {
       * $group: {
       * _id: "$lastName",
       * firstName: { $first: "$firstName" }
       * }
       * },
       * {
       * $sort: {
       * "_id": 1, "firstName": 1
       * }
       * },
       * {
       * $project: {
       * "_id": 0,
       * "firstName": 1,
       * "lastName": "$_id"
       * }
       * },
       * {
       * $limit: 20
       * }
       * ])
       *
       * @param namePrefix first prefix
       * @return stream of object
       */
      override def getAutosuggestName(namePrefix: String, rating: Double, params: ReqParams): Stream[F, AutoNameRec] = {
        val queryPipeline = genAutonameFilter(namePrefix, rating, params)

        Stream.eval(Clock[F].monotonic).flatMap { start =>
          compFx.aggregateWithCodec[AutoNameRec](queryPipeline).stream
            .onFinalize { // Ensure that logging operation happens once after all stream processing is complete
              for {
                end <- Clock[F].monotonic
                _ <- Sync[F].delay(L.info(s"getAutosuggestName took {} ms", (end - start).toMillis))
              } yield ()
            }
        }
      }

      /**
       * db.getCollection("title_principals_namerating").aggregate([
       * {
       * $match: {
       * primaryTitle: { $regex: "^^Gone with the" },
       * "startYear": { "$gte": 2015, "$lte": 2024 },
       * "genresList": { "$in": ["Adventure"] },
       * "titleType": "movie",
       * "isAdult": 0,
       * "numVotes": { "$gte": 50 },
       * "$or": [{ "averageRating": { "$exists": false } },
       * { "averageRating": { "$gte": 4.0 } }]
       * }
       * },
       * {
       * $group: {
       * _id: "$primaryTitle",
       * }
       * },
       * {
       * $sort: { "primaryTitle": 1 }
       * },
       * {
       * $limit: 20
       * }
       * ])
       *
       * @param titlePrefix title regex
       * @return stream of object
       */
      override def getAutosuggestTitle(titlePrefix: String, rating: Double, params: ReqParams): Stream[F, AutoTitleRec] = {
        val queryPipeline = genAutotitleFilter(titlePrefix, rating, params)

        Stream.eval(Clock[F].monotonic).flatMap { start =>
          compFx.aggregateWithCodec[AutoTitleRec](queryPipeline).stream
            .onFinalize { // Ensure that logging operation happens once after all stream processing is complete
              for {
                end <- Clock[F].monotonic
                _ <- Sync[F].delay(L.info(s"getAutosuggestTitle took {} ms", (end - start).toMillis))
              } yield ()
            }
        }
      }

      private def getPath(imdb: String): F[PathRec] = for {
        pathStr <- Sync[F].delay(s"$imagePath/$imdb/S")
        pathReq <- Sync[F].delay(Request[F](Method.GET, Uri.unsafeFromString(pathStr)))
        (pathTime, pathVal) <- Clock[F].timed(client.expect(pathReq)(jsonOf[F, PathRec]))
        _ <- Sync[F].delay(L.info(s"pathStr {} imdb {} time {} ms", pathStr, imdb, pathTime.toMillis))
      } yield pathVal

      override def getByTitlePath(optTitle: Option[String], rating: Double, params: ReqParams): Stream[F, TitleRecPath] = {
        Stream.eval(Clock[F].monotonic).flatMap { overallStart =>
          val queryPipeline = genQueryPipeline(genTitleFilter(optTitle, rating, params))

          // connect the streams
          val titleRecPathStream = compFx.aggregateWithCodec[TitleRecPath](queryPipeline).stream
          val enhancedStream = getTitlePathsStream(titleRecPathStream, chunkSize)

          enhancedStream.onFinalize {
            for {
              endEnh <- Clock[F].monotonic
              _ <- Sync[F].delay(L.info(s"getByTitlePathAgg took {} ms", (endEnh - overallStart).toMillis))
            } yield ()
          }
        }
      }
    }
}
