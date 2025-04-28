package com.iscs.ratingbunny.repos

import cats.effect.*
import cats.syntax.all.*
import com.iscs.ratingbunny.model.Requests.ReqParams
import com.typesafe.scalalogging.Logger
import io.circe.Encoder
import io.circe.syntax.*
import io.circe.generic.auto.*
import mongo4cats.bson.Document
import mongo4cats.bson.syntax.*
import mongo4cats.collection.MongoCollection
import org.mongodb.scala.model.{Filters => JFilters, IndexOptions, Indexes, UpdateOptions, Updates => JUpdates}
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
final class HistoryRepo[F[_]: Async](private val coll: MongoCollection[F, Document]) {
  private val L = Logger[this.type]

  // ---------- helpers -------------------------------------------------------
  private def now: F[Instant] = Clock[F].realTimeInstant

  private def sha256(s: String): String =
    MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8")).map("%02x" format _).mkString

  private def buildSigAndDoc(userId: String, params: ReqParams): (String, Document) = {
    val json    = params.asJson.dropNullValues.noSpaces
    val sig     = sha256(json)
    val created = Instant.now()
    val doc     = Document("userId" := userId, "createdAt" := created, "params" := Document.parse(json), "sig" := sig, "hits" := 1)
    sig -> doc
  }

  // ---------- public API ----------------------------------------------------

  /** Upsert or bump the `hits` counter for an identical search. */
  def log(userId: String, params: ReqParams): F[Unit] = {
    val (sig, newDoc) = buildSigAndDoc(userId, params)
    for {
      t <- now
      _ <- Sync[F].delay(L.info(s"history log $userId $sig $newDoc"))
      _ <- coll
        .updateOne(
          filter = JFilters.and(JFilters.equal("userId", userId), JFilters.equal("sig", sig)),
          update = JUpdates.combine(
            JUpdates.setOnInsert("params", newDoc("params")),
            JUpdates.set("createdAt", t),
            JUpdates.inc("hits", 1)
          ),
          options = UpdateOptions().upsert(true)
        )
        .void
    } yield ()
  }

  /** Latest *n* records for a user (default 10). */
  /*  def latest(userId: String, limit: Int = 10): fs2.Stream[F, Document] =
    coll.find(JFilters.equal("userId", userId)).sort(Document("createdAt" := -1)).limit(limit)*/

  /** Fetch an entry by its signature. */
  /*  def bySig(userId: String, sig: String): F[Option[Document]] =
    coll.find(JFilters.and(JFilters.equal("userId", userId), JFilters.equal("sig", sig))).first.map(Option(_))*/

  // ---------- collection bootstrap -----------------------------------------
  def ensureIndexes: F[Unit] = {
    val userDate  = coll.createIndex(Indexes.ascending("userId", "createdAt"), IndexOptions().background(true))
    val sigUnique = coll.createIndex(Indexes.ascending("sig"), IndexOptions().unique(true).background(true))
    (userDate, sigUnique).mapN((_, _) => ())
  }
}

object HistoryRepo {

  /** Build repo from an existing `MongoDatabase` (already passed around by your `Server.getServices`).
    */
  def make[F[_]: Async](db: mongo4cats.database.MongoDatabase[F]): F[HistoryRepo[F]] =
    for {
      coll <- db.getCollection("user_history")
      repo = new HistoryRepo[F](coll)
      _ <- repo.ensureIndexes
    } yield repo
}
