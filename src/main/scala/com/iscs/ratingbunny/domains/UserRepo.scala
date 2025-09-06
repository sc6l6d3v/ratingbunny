package com.iscs.ratingbunny.domains

import cats.effect.*
import cats.implicits.*
import mongo4cats.circe.*
import mongo4cats.collection.MongoCollection
import org.mongodb.scala.model.{Updates as JUpdates}

trait UserRepo[F[_]]:
  def findByEmail(email: String): F[Option[UserDoc]]
  def insert(u: UserDoc): F[Unit]
  def findByUserId(id: String): F[Option[UserDoc]]
  def findByVerificationTokenHash(hash: String): F[Option[UserDoc]]
  def markEmailVerified(uid: String): F[Unit]

class UserRepoImpl[F[_]: Async](collection: MongoCollection[F, UserDoc]) extends UserRepo[F] with QuerySetup:
  override def findByEmail(email: String): F[Option[UserDoc]] = collection.find(feq("email", email)).first
  override def insert(u: UserDoc): F[Unit]                    = collection.insertOne(u).void
  override def findByUserId(uid: String): F[Option[UserDoc]]  = collection.find(feq("userid", uid)).first
  override def findByVerificationTokenHash(hash: String): F[Option[UserDoc]] =
    collection.find(feq("verificationTokenHash", hash)).first
  override def markEmailVerified(uid: String): F[Unit] =
    val update = JUpdates.combine(
      JUpdates.set("emailVerified", true),
      JUpdates.unset("verificationTokenHash"),
      JUpdates.unset("verificationExpires")
    )
    collection.updateOne(feq("userid", uid), update).void
