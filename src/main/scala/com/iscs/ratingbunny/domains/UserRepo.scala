package com.iscs.ratingbunny.domains

import cats.data.EitherT
import cats.effect.*
import cats.effect.implicits.*
import cats.implicits.*
import com.iscs.ratingbunny.util.PasswordHasher
import com.mongodb.ErrorCategory.DUPLICATE_KEY
import com.mongodb.{ErrorCategory, MongoWriteException}
import mongo4cats.circe.*
import mongo4cats.collection.MongoCollection

trait UserRepo[F[_]] {
  def findByEmail(email: String): F[Option[UserDoc]]
  def insert(u: UserDoc): F[Unit]
  def findByUserId(id: String): F[Option[UserDoc]]
}

class UserRepoImpl[F[_]: Async](collection: MongoCollection[F, UserDoc]) extends UserRepo[F] with QuerySetup {
  override def findByEmail(email: String): F[Option[UserDoc]] = collection.find(feq("email", email)).first
  override def insert(u: UserDoc): F[Unit]                    = collection.insertOne(u).void
  override def findByUserId(id: String): F[Option[UserDoc]]   = collection.find(feq("_id", id)).first
}
