package com.iscs.ratingbunny.repos

import cats.effect.*
import cats.syntax.all.*
import com.iscs.ratingbunny.domains.QuerySetup
import com.iscs.ratingbunny.model.Requests.ReqParams
import com.iscs.ratingbunny.util.DeterministicHash
import com.typesafe.scalalogging.Logger
import fs2.Stream
import io.circe.{Encoder, Json}
import io.circe.parser.decode
import io.circe.syntax.*
import io.circe.generic.auto.*
import mongo4cats.bson.Document
import mongo4cats.bson.syntax.*
import mongo4cats.collection.MongoCollection
import mongo4cats.models.collection.{IndexOptions, UpdateOptions}
import mongo4cats.operations.Index
import org.mongodb.scala.model.{Filters as JFilters, Updates as JUpdates}
import com.mongodb.MongoWriteException
import com.mongodb.ErrorCategory.DUPLICATE_KEY

import java.time.Instant

/** One entry of a user's search history as the API returns it: the search's signature, when it was last run, the parameters it was last run
  * with, and how many times it has been run.
  */
final case class HistoryEntry(sig: String, createdAt: Instant, params: ReqParams, hits: Long)

object HistoryEntry:
  // Written out by hand so `createdAt` is an ISO-8601 string wherever this is encoded; mongo4cats.circe's Instant encoder, if in
  // scope, would emit Mongo's {"$date": …} form instead.
  given Encoder[HistoryEntry] = Encoder.instance: e =>
    Json.obj(
      "sig"       -> e.sig.asJson,
      "createdAt" -> Json.fromString(e.createdAt.toString),
      "params"    -> e.params.asJson.dropNullValues,
      "hits"      -> e.hits.asJson
    )

/** Repository that persists/distills every search request sent from the React front‑end. It works with a plain `MongoCollection[F,
  * Document]` so you don’t need an explicit codec.
  */
final class HistoryRepo[F[_]: Async](private[repos] val coll: MongoCollection[F, Document]) extends QuerySetup:
  private val L                = Logger[this.type]
  private val idxUserDate      = "uid_date_idx"          // desired explicit name
  private val idxUserDateDef   = "userId_1_createdAt_-1" // driver default name
  private val idxUserSigUnq    = "uid_sig_unique_idx"
  private val idxUserSigUnqDef = "userId_1_sig_1"

  // ---------- helpers -------------------------------------------------------
  private def now: F[Instant] = Clock[F].realTimeInstant

  private def tidy(values: Option[List[String]]): Option[List[String]] =
    values.map(_.distinct.sorted).filter(_.nonEmpty)

  /** The search a request denotes: spelling variations folded away and the presentation-only `sortType` dropped. */
  private def canonical(params: ReqParams): ReqParams =
    ReqParams(
      query = params.query.map(_.trim.toLowerCase).filter(_.nonEmpty),
      votes = params.votes,
      year = params.year.filter(_.nonEmpty).map(ys => List(ys.min, ys.max)),
      genre = tidy(params.genre),
      titleType = tidy(params.titleType),
      isAdult = params.isAdult,
      searchType = params.searchType,
      lang = params.lang
    )

  private def toEntry(doc: Document): Option[HistoryEntry] =
    for
      sig       <- doc.getString("sig")
      createdAt <- doc.get("createdAt").flatMap(_.asInstant)
      hits      <- doc.get("hits").flatMap(h => h.asInt.map(_.toLong).orElse(h.asLong).orElse(h.asDouble.map(_.toLong)))
      params    <- doc.getDocument("params").flatMap(p => decode[ReqParams](p.toJson).toOption)
    yield HistoryEntry(sig, createdAt, params, hits)

  private def entries(docs: Stream[F, Document]): Stream[F, HistoryEntry] =
    docs.flatMap: doc =>
      toEntry(doc) match
        case Some(entry) => Stream.emit(entry)
        case None =>
          val id = doc.getObjectId("_id").fold("without _id")(_.toHexString)
          Stream.exec(Sync[F].delay(L.warn(s"skipping unreadable history entry $id")))

  /** Signature of the search `params` denotes; equivalent spellings of one search share it. */
  def sigFor(params: ReqParams): String =
    DeterministicHash.sha256(canonical(params).asJson.dropNullValues.noSpaces)

  // ---------- public API ----------------------------------------------------

  /** Upsert or bump the `hits` counter for an equivalent search, keeping the parameters it was last run with. */
  def log(userId: String, params: ReqParams): F[Unit] =
    val sig    = sigFor(params)
    val sent   = Document.parse(params.asJson.dropNullValues.noSpaces)
    val filter = JFilters.and(JFilters.equal("userId", userId), JFilters.equal("sig", sig))
    for
      t <- now
      _ <- Sync[F].delay(L.info(s"history log $userId $sig $params"))
      _ <-
        val update = JUpdates.combine(
          JUpdates.set("params", sent),
          JUpdates.set("createdAt", t),
          JUpdates.inc("hits", 1)
        )
        coll
          .updateOne(filter = filter, update = update, options = UpdateOptions().upsert(true))
          .void
          .recoverWith:
            // two concurrent first-time upserts of one search: the loser bumps the winner's entry
            case mw: MongoWriteException if mw.getError.getCategory == DUPLICATE_KEY =>
              coll.updateOne(filter = filter, update = update, options = UpdateOptions()).void
          .onError:
            case e => Sync[F].delay(L.error(s"history log failed for $userId $sig", e))
    yield ()

  /** Latest *n* records for a user (default 10). */
  def latest(userId: String, limit: Int = 10): Stream[F, HistoryEntry] =
    entries(coll.find(feq("userId", userId)).sort(Document("createdAt" := -1)).limit(limit).stream)

  /** Most frequent *n* records for a user (default 10), most recent first among equals. */
  def popular(userId: String, limit: Int = 10): Stream[F, HistoryEntry] =
    entries(coll.find(feq("userId", userId)).sort(Document("hits" := -1, "createdAt" := -1)).limit(limit).stream)

  /** Fetch an entry by its signature. */
  def bySig(userId: String, sig: String): F[Option[Document]] =
    val query = feq("userId", userId).add(("sig", sig))
    coll.find(query).first

  /** The signature of the user's entry for the search `params` denotes, if they have one. */
  def byParams(userId: String, params: ReqParams): F[Option[String]] =
    val sig = sigFor(params)
    bySig(userId, sig).map(_.as(sig))

  /** Create both indexes if they don’t already exist. Safe to call on every start‑up.
    */
  def ensureIndexes: F[Unit] =
    for
      idxDocs <- coll.listIndexes // F[Iterable[Document]]
      existing = idxDocs.flatMap(_.getString("name").toList).toSet
      // userId + createdAt compound index -----------------------------
      _ <-
        if (existing(idxUserDate) || existing(idxUserDateDef)) Async[F].unit
        else
          coll
            .createIndex(
              Index.ascending("userId").descending("createdAt"),
              IndexOptions(background = true).name(idxUserDate)
            )
            .void
      // unique userId+sig index ---------------------------------------
      _ <-
        if (existing(idxUserSigUnq) || existing(idxUserSigUnqDef)) Async[F].unit
        else
          coll
            .createIndex(
              Index.ascending("userId").ascending("sig"),
              IndexOptions(background = true, unique = true).name(idxUserSigUnq)
            )
            .void
    yield ()

object HistoryRepo:

  /** Build repo from an existing `MongoDatabase` (already passed around by your `Server.getServices`).
    */
  def make[F[_]: Async](db: mongo4cats.database.MongoDatabase[F]): F[HistoryRepo[F]] =
    for
      coll <- db.getCollection("user_history")
      repo = new HistoryRepo[F](coll)
      _ <- repo.ensureIndexes
    yield repo
