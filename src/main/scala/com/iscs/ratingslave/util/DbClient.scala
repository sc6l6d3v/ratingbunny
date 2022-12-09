package com.iscs.ratingslave.util

import cats.effect.{Async, Resource, Sync}
import cats.implicits._
import cats.syntax.all._
import com.iscs.ratingslave.config.MongodbConfig
import mongo4cats.bson.Document
import mongo4cats.client.MongoClient
import mongo4cats.collection.MongoCollection
import mongo4cats.database.MongoDatabase

class DbClient[F[_]: Sync : Async](val config: MongodbConfig) {

  val dbResource: Resource[F, MongoClient[F]] = MongoClient.fromConnectionString(config.url)

/*  def getFxMap(db: F[MongoDatabase[F]]): F[Map[String, F[MongoCollection[F, Document]]]] = for {
    mongoDb <- db
    mongoMaps <- Sync[F].delay(collNames.foldLeft(Map.empty[String, F[MongoCollection[F, Document]]]) { case (acc, elt) =>
      acc ++ Map(elt -> mongoDb.getCollection(elt))
    })
  } yield mongoMaps

  def getCollection(db: F[MongoDatabase[F]], collName: String): F[MongoCollection[F, Document]] = for {
    fxMap <- getFxMap(db)
    maybeName <- Sync[F].delay(if (fxMap.contains(collName)) collName else "DONTUSE")
    coll <- fxMap(maybeName)
  } yield coll*/


/*  def getCollections(db: MongoDatabase[F],
                     collNames: List[String]): F[(List[String], MongoCollection[F, Document])] =  {
    collNames.foldLeft(Map.empty[String, MongoCollection[F, Document]]) { case (acc, elt) =>

      for {
        collName <- Sync[F].delay(elt)
        coll <- db.getCollection(collName)
      } yield collName -> coll
    }
  }*/

//  val db: MongoDatabase = client.getDatabase(dbName)

//  def dbFX(collection: MongoCollection[Document]) = new MongoCollectionEffect[Document](collection)

/*
  val fxMap: Map[String, MongoCollectionEffect[Document]] =
    collNames.map { name =>
      name -> dbFX(db.getCollection(name))
    }.toMap
*/
}
