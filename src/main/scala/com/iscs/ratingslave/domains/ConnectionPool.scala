package com.iscs.ratingslave.domains

import cats.effect._
import cats.implicits._
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._
import mongo4cats.bson.Document
import mongo4cats.database.MongoDatabase
import org.mongodb.scala.bson.BsonDocument

import scala.language.implicitConversions

trait ConnectionPool[F[_]] {
  def getCPStats: F[Json]
}

object ConnectionPool {
  private val dbcmd = BsonDocument("serverStatus" -> 1)
  private val attrib = "connections"

  def apply[F[_]](implicit ev: ConnectionPool[F]): ConnectionPool[F] = ev

  private case class Connections(
                          current: Option[Int] = None,
                          available: Option[Int] = None,
                          totalCreated: Option[Int] = None,
                          active: Option[Int] = None,
                          threaded: Option[Int] = None,
                          exhaustIsMaster: Option[Int] = None,
                          exhaustHello: Option[Int] = None,
                          awaitingTopologyChanges: Option[Int] = None
                        )

  def impl[F[_] : Sync](db: MongoDatabase[F]): ConnectionPool[F] = {

    def getStats(statDoc: Document): Json = {
      val connectionsDoc = statDoc.get(attrib).map(_.asDocument.getOrElse(Document()))
      val connections = Connections(
        current = connectionsDoc.get("current").map{_.asInt.get},
        available = connectionsDoc.get("available").map(_.asInt.get),
        totalCreated = connectionsDoc.get("totalCreated").map(_.asInt.get),
        active = connectionsDoc.get("active").map(_.asInt.get),
        threaded = connectionsDoc.get("threaded").map(_.asInt.get),
        exhaustIsMaster = connectionsDoc.get("exhaustIsMaster").map(_.asInt.get),
        exhaustHello = connectionsDoc.get("exhaustHello").map(_.asInt.get),
        awaitingTopologyChanges = connectionsDoc.get("awaitingTopologyChanges").map(_.asInt.get)
      )
      connections.asJson
    }

    new ConnectionPool[F] {
      override def getCPStats: F[Json] = for {
        doc <- db.runCommand(dbcmd)
      } yield getStats(doc)
    }
  }
}
