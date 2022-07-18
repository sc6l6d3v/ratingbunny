package com.iscs.ratingslave.util

import cats.effect.{ConcurrentEffect, Effect, Sync}
import com.iscs.ratingslave.config.MongodbConfig
import org.mongodb.scala.bson.Document
import org.mongodb.scala.{MongoClient, MongoCollection, MongoDatabase}

case class DbClient[F[_]: Effect: Sync](config: MongodbConfig, dbName: String, collNames: List[String])(implicit E: Effect[F]) extends AutoCloseable {
  private val client = MongoClient(config.settings)

  val db: MongoDatabase = client.getDatabase(dbName)

  def dbFX(collection: MongoCollection[Document]) = new MongoCollectionEffect[Document](collection)

  val fxMap: Map[String, MongoCollectionEffect[Document]] =
    collNames.map { name =>
      name -> dbFX(db.getCollection(name))
    }.toMap

  override def close(): Unit = client.close()
}

object DbClient {
  def apply[F[_]](client: MongodbConfig, names: List[String])
                 (implicit E: ConcurrentEffect[F]): F[DbClient[F]] =
    E.delay(DbClient(client, names.head, names.tail))
}
