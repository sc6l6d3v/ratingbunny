package com.iscs.ratingbunny.domains

import cats.effect.*
import cats.implicits.*
import mongo4cats.circe.*
import mongo4cats.collection.MongoCollection

trait UserRepo[F[_]]:
  def findByEmail(email: String): F[Option[UserDoc]]
  def insert(u: UserDoc): F[Unit]
  def findByUserId(id: String): F[Option[UserDoc]]

class UserRepoImpl[F[_]: Async](collection: MongoCollection[F, UserDoc]) extends UserRepo[F] with QuerySetup:
  override def findByEmail(email: String): F[Option[UserDoc]] = collection.find(feq("email", email)).first
  override def insert(u: UserDoc): F[Unit]                    = collection.insertOne(u).void
  override def findByUserId(uid: String): F[Option[UserDoc]]  = collection.find(feq("userid", uid)).first
