package com.iscs.ratingbunny.domains

import cats.effect.*
import cats.implicits.*
import mongo4cats.bson.Document
import mongo4cats.circe.*
import mongo4cats.collection.MongoCollection
import mongo4cats.models.collection.IndexOptions
import mongo4cats.operations.Index
import org.mongodb.scala.model.Updates as JUpdates
import com.iscs.ratingbunny.util.DeterministicHash
import java.time.Instant

trait UserRepo[F[_]]:
  def findByEmail(email: String): F[Option[UserDoc]]
  def insert(u: UserDoc): F[Unit]
  def findByUserId(id: String): F[Option[UserDoc]]
  def findByVerificationToken(token: String): F[Option[UserDoc]]
  def markEmailVerified(uid: String): F[Unit]
  def updateSubscription(uid: String, plan: Plan, status: SubscriptionStatus, trialEndsAt: Option[Instant]): F[Unit]

class UserRepoImpl[F[_]: Async](collection: MongoCollection[F, UserDoc]) extends UserRepo[F] with QuerySetup:
  override def findByEmail(email: String): F[Option[UserDoc]] = collection.find(feq("email", email)).first
  override def insert(u: UserDoc): F[Unit]                    = collection.insertOne(u).void
  override def findByUserId(uid: String): F[Option[UserDoc]]  = collection.find(feq("userid", uid)).first

  override def findByVerificationToken(token: String): F[Option[UserDoc]] =
    val hash = DeterministicHash.sha256(token)
    collection.find(feq("verificationTokenHash", hash)).first

  override def markEmailVerified(uid: String): F[Unit] =
    val update = JUpdates.combine(
      JUpdates.set("emailVerified", true),
      JUpdates.unset("verificationTokenHash"),
      JUpdates.unset("verificationExpires")
    )
    collection.updateOne(feq("userid", uid), update).void

  override def updateSubscription(
      uid: String,
      plan: Plan,
      status: SubscriptionStatus,
      trialEndsAt: Option[Instant]
  ): F[Unit] =
    val update = JUpdates.combine(
      JUpdates.set("plan", plan),
      JUpdates.set("status", status),
      trialEndsAt.fold(JUpdates.unset("trialEndsAt"))(inst => JUpdates.set("trialEndsAt", inst))
    )
    collection.updateOne(feq("userid", uid), update).void

  private val idxVerif     = "verificationTokenHash_idx"
  private val idxUserId    = "userid_unique_idx"
  private val idxEmailNorm = "email_norm_unique_idx"

  /** Unique indexes that settle signup races on userid and email. Existing indexes are matched by key, not name: the production ones were
    * created by hand under other names, and creating a same-key index under a new name is an error. A failure is logged rather than raised
    * so legacy duplicates cannot stop the app from starting.
    */
  def ensureIndexes: F[Unit] =
    for
      idxDocs <- collection.listIndexes
      _       <- ensureUnique(idxDocs, "verificationTokenHash", idxVerif, sparse = true)
      _       <- ensureUnique(idxDocs, "userid", idxUserId, sparse = false)
      _       <- ensureUnique(idxDocs, "email_norm", idxEmailNorm, sparse = false)
    yield ()

  private def ensureUnique(idxDocs: Iterable[Document], field: String, name: String, sparse: Boolean): F[Unit] =
    idxDocs.find(_.getDocument("key").exists(_.keys == Set(field))) match
      case Some(idx) if idx.getBoolean("unique").contains(true) => Async[F].unit
      case Some(idx) =>
        Sync[F].delay(L.error(s"users.$field has non-unique index ${idx.getString("name").getOrElse("?")}; uniqueness is not enforced"))
      case None =>
        collection
          .createIndex(Index.ascending(field), IndexOptions(background = true, unique = true, sparse = sparse).name(name))
          .void
          .handleErrorWith: e =>
            Sync[F].delay(L.error(s"could not create unique index on users.$field: ${e.getMessage}", e))

object UserRepoImpl:
  def make[F[_]: Async](collection: MongoCollection[F, UserDoc]): F[UserRepo[F]] =
    val repo = new UserRepoImpl[F](collection)
    repo.ensureIndexes.as(repo)
