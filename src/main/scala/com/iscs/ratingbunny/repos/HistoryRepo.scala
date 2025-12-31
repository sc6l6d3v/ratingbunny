package com.iscs.ratingbunny.repos

import cats.effect.*
import cats.syntax.all.*
import com.iscs.ratingbunny.domains.QuerySetup
import com.iscs.ratingbunny.model.Requests.ReqParams
import com.typesafe.scalalogging.Logger
import fs2.Stream
import io.circe.Encoder
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

import java.security.MessageDigest
import java.time.Instant

/** Mongo representation of one entry in `user_history`. */
final case class HistoryDoc(
    userId: String,
    createdAt: Instant,
    params: ReqParams,
    sig: String,
    hits: Int = 1
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

  private def sha256(s: String): String =
    MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8")).map("%02x" format _).mkString

  private def genString(params: ReqParams): String = params.asJson.dropNullValues.noSpaces

  /** Compute the signature for a given set of params (must match the `log` signature). */
  def sigFor(params: ReqParams): String =
    val json = genString(params)
    sha256(json)

  private def buildSigAndDoc(userId: String, params: ReqParams): (String, Document) =
    val json    = genString(params)
    val sig     = sha256(json)
    val created = Instant.now()
    val doc     = Document("userId" := userId, "createdAt" := created, "params" := Document.parse(json), "sig" := sig, "hits" := 1)
    sig -> doc

  // ---------- public API ----------------------------------------------------

  /** Upsert or bump the `hits` counter for an identical search. */
  def log(userId: String, params: ReqParams): F[Unit] =
    val (sig, newDoc) = buildSigAndDoc(userId, params)
    val filter        = JFilters.and(JFilters.equal("userId", userId), JFilters.equal("sig", sig))
    for
      t <- now
      _ <- Sync[F].delay(L.info(s"history log $userId $sig $newDoc"))
      _ <-
        val update = JUpdates.combine(
          JUpdates.setOnInsert("params", newDoc("params")),
          JUpdates.set("createdAt", t),
          JUpdates.inc("hits", 1)
        )
        coll
          .updateOne(filter = filter, update = update, options = UpdateOptions().upsert(true))
          .void
          .handleErrorWith:
            case mw: MongoWriteException if mw.getError.getCategory == DUPLICATE_KEY =>
              coll.updateOne(filter = filter, update = update, options = UpdateOptions()).void
            case _ => Async[F].unit
    yield ()

  /** Latest *n* records for a user (default 10). */
  def latest(userId: String, limit: Int = 10): Stream[F, Document] =
    coll.find(feq("userId", userId)).sort(Document("createdAt" := -1)).limit(limit).stream

  /** Most frequent *n* records for a user (default 10). */
  def popular(userId: String, limit: Int = 10): Stream[F, Document] =
    coll.find(feq("userId", userId)).sort(Document("hits" := -1)).limit(limit).stream

  /** Fetch an entry by its signature. */
  def bySig(userId: String, sig: String): F[Option[Document]] =
    val query = feq("userId", userId).add(("sig", sig))
    coll.find(query).first

  /** Fetch an entry by the request params (recomputes sig and delegates to `bySig`). */
  def byParams(userId: String, params: ReqParams): F[Option[Document]] =
    val sig = sigFor(params)
    bySig(userId, sig).map(_.map(_ => Document("sig" := sig)))

  /** Fetch all users that have logged this signature. */
  def getUsers(sig: String): F[Option[Document]] =
    coll
      .find(feq("sig", sig))
      .projection(Document("_id" := 0, "userId" := 1))
      .stream
      .compile
      .toList
      .map: docs =>
        val users = docs.flatMap(_.getString("userId").toList)
        Option.when(users.nonEmpty)(Document("userIds" := users))

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
