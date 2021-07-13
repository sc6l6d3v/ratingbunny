package com.iscs.releaseScraper.util

import cats.effect.implicits._
import cats.effect.{ConcurrentEffect, IO}
import cats.implicits._
import com.mongodb.client.model.WriteModel
import com.mongodb.client.result.UpdateResult
import fs2._
import fs2.concurrent.Queue
import org.bson.conversions.Bson
import org.mongodb.scala.bson.{BsonArray, BsonBoolean, BsonDocument, BsonNumber, BsonString}
import org.mongodb.scala.model.Projections._
import org.mongodb.scala.result.{DeleteResult, InsertManyResult, InsertOneResult}
import org.mongodb.scala.{BulkWriteResult, MongoCollection, Observable, bson}

import scala.reflect.ClassTag

object imports {
  final val Mongo = com.iscs.releaseScraper.util.Mongo

  def toAsync[F[_]: ConcurrentEffect, T](obs: Observable[T]): F[Option[T]] =
    ConcurrentEffect[F].async[Option[T]] { (cb: Either[Throwable, Option[T]] => Unit) =>
      obs.subscribe(
        (result: T) => cb(Right(result.some)),
        (ex: Throwable) => cb(Left(ex)),
        () => cb(Option.empty[T].asRight))
    }

  def toStream[F[_]: ConcurrentEffect, T](observ:Observable[T]): Stream[F, T] =
    for {
      que <- Stream.eval(Queue.noneTerminated[F,Either[Throwable, T]])
      _ <- Stream.eval{
        ConcurrentEffect[F].delay(observ.subscribe(
          (next: T) =>
            que.enqueue1(Right(next).some)
              .runAsync(_ => IO.unit).unsafeRunSync(),
          (ex: Throwable) =>
            que.enqueue1(Left(ex).some)
              .runAsync(_ => IO.unit).unsafeRunSync(),
          () =>
            que.enqueue1(Option.empty[Either[Throwable, T]])
              .runAsync(_ => IO.unit).unsafeRunSync()
        ))
      }
      row <- que.dequeue.rethrow
    } yield row
}

class MongoCollectionEffect[A](val underlying: MongoCollection[A])(implicit c: ClassTag[A]) {
  private val batchsize = 1000

  import imports._

  def getProjectionFields(projSet: Map[String, Boolean]): List[Bson] =
    projSet.map{ case(field, action) =>
      if (action) include(field) else exclude(field)
    }.toList

  def getProjections(projSet: List[Bson]): Bson = fields(projSet:_*)

  def getCompareFilter(compareOp: String, fieldName: String, compVal: Boolean): Bson =
    bson.BsonDocument(List((s"$$$compareOp", BsonArray(BsonString(s"$$$fieldName"), BsonBoolean(compVal)))))

  def getCompareFilter(compareOp: String, fieldName: String, compVal: String): Bson =
    bson.BsonDocument(List((s"$$$compareOp", BsonArray(BsonString(s"$$$fieldName"), BsonString(compVal)))))

  def getCompareFilter(compareOp: String, fieldName: String, compVal: Double): Bson =
    bson.BsonDocument(List((s"$$$compareOp", BsonArray(BsonString(s"$$$fieldName"), BsonNumber(compVal)))))

  def getCompareFilter(compareOp: String, fieldName: String, compVal: Int): Bson =
    bson.BsonDocument(List((s"$$$compareOp", BsonArray(BsonString(s"$$$fieldName"), BsonNumber(compVal)))))

  def getCondFilter(fieldName: String, item: String, cond: Bson): Bson = {
    val inputAsCond = new BsonDocument()
      .append("input", BsonString(s"$$$fieldName"))
      .append("as", BsonString(s"$item"))
      .append("cond", cond.toBsonDocument)
    val filterBson = new BsonDocument("$filter", inputAsCond)
    computed(fieldName, filterBson)
  }

  def aggregate[F[_]: ConcurrentEffect](stages: Seq[Bson]): Stream[F, A] =
    toStream(underlying.aggregate(stages))

  def bulkWrite[F[_]: ConcurrentEffect](requests: Seq[WriteModel[A]]): F[Option[BulkWriteResult]] =
    toAsync(underlying.bulkWrite(requests))

  def distinct[F[_]: ConcurrentEffect](key: String): Stream[F,A] = toStream(underlying.distinct(key))

  def findOne[F[_]: ConcurrentEffect](filter: Bson): F[Option[A]] = toAsync(underlying.find(filter)
    .limit(1).first())

  def find[F[_]: ConcurrentEffect](filter: Bson): Stream[F,A] =
    toStream(underlying.find(filter).batchSize(batchsize))

  def find[F[_]: ConcurrentEffect](filter: Bson, projections: Map[String, Boolean]): Stream[F,A] =
    toStream(underlying.find(filter).batchSize(batchsize)
        .projection(fields(getProjectionFields(projections):_*)))

  def find[F[_]: ConcurrentEffect](filter: Bson, limit: Int, offset: Int): Stream[F,A] =
    toStream(underlying.find(filter).skip(offset).limit(limit))

  def find[F[_]: ConcurrentEffect](filter: Bson, limit: Int, offset: Int, projections: Map[String, Boolean]): Stream[F,A] =
    toStream(
      underlying.find(filter)
        .projection(fields(getProjectionFields(projections):_*))
        .skip(offset)
        .limit(limit)
    )

  def find[F[_]: ConcurrentEffect](filter: Bson, limit: Int, offset: Int, projections: Map[String, Boolean], sortFields: Bson): Stream[F,A] =
    toStream(
      underlying.find(filter)
        .projection(fields(getProjectionFields(projections):_*))
        .skip(offset)
        .limit(limit)
        .sort(sortFields)
    )

  def count[F[_]: ConcurrentEffect]: F[Option[Long]] = toAsync(underlying.countDocuments())

  def count[F[_]: ConcurrentEffect](filter: Bson): F[Option[Long]] = toAsync(underlying.countDocuments(filter))

  def insertOne[F[_]: ConcurrentEffect](document: A): F[Option[InsertOneResult]] =
    toAsync(underlying.insertOne(document))

  def insertMany[F[_]: ConcurrentEffect](documents: Seq[A]): F[Option[InsertManyResult]] =
    toAsync(underlying.insertMany(documents))

  def removeOne[F[_]: ConcurrentEffect](filter: Bson): F[Option[DeleteResult]] =
    toAsync(underlying.deleteOne(filter))

  def removeMany[F[_]: ConcurrentEffect](filter: Bson): F[Option[DeleteResult]] =
    toAsync(underlying.deleteMany(filter))

  def updateOne[F[_]: ConcurrentEffect](filter: Bson, update: Bson): F[Option[UpdateResult]] =
    toAsync(underlying.updateOne(filter, update))

  def updateMany[F[_]: ConcurrentEffect](filter: Bson, update: Bson): F[Option[UpdateResult]] =
    toAsync(underlying.updateMany(filter, update))
}

