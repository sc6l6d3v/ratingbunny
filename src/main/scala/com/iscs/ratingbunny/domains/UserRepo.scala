package com.iscs.ratingbunny.domains

import cats.effect.*
import cats.implicits.*
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
      trialEndsAt.fold[JUpdates.Bson] (JUpdates.unset("trialEndsAt"))(inst => JUpdates.set("trialEndsAt", inst))
    )
    collection.updateOne(feq("userid", uid), update).void

  private val idxVerif    = "verificationTokenHash_idx"
  private val idxVerifDef = "verificationTokenHash_1"

  def ensureIndexes: F[Unit] =
    for
      idxDocs <- collection.listIndexes
      existing = idxDocs.flatMap(_.getString("name").toList).toSet
      _ <-
        if existing(idxVerif) || existing(idxVerifDef) then Async[F].unit
        else
          collection
            .createIndex(
              Index.ascending("verificationTokenHash"),
              IndexOptions(background = true, unique = true, sparse = true).name(idxVerif)
            )
            .void
    yield ()

object UserRepoImpl:
  def make[F[_]: Async](collection: MongoCollection[F, UserDoc]): F[UserRepo[F]] =
    val repo = new UserRepoImpl[F](collection)
    repo.ensureIndexes.as(repo)
