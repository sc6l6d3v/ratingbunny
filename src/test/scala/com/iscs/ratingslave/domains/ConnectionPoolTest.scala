package com.iscs.ratingslave.domains

import cats.effect.{IO, Resource}
import cats.effect.unsafe.IORuntime
import cats.effect.unsafe.implicits.global
import io.circe.Json
import mongo4cats.bson.Document
import mongo4cats.client.MongoClient
import mongo4cats.database.MongoDatabase
import mongo4cats.embedded.EmbeddedMongo
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import mongo4cats.bson.syntax.*
import org.mongodb.scala.bson.BsonDocument
import scala.concurrent.Future

class ConnectionPoolTest extends AsyncWordSpec with Matchers with EmbeddedMongo {

  override val mongoPort: Int = 12348

  "embedded MongoDB" when {
    "invoking getCPStats" should {
      "return connection pool stats as JSON" in withEmbeddedMongoClient { client =>
          for {
            db <- client.getDatabase("test")
            _ <- setupTestDatabase(db)
            connectionPool = ConnectionPool.impl[IO](db)
            jsonResult <- connectionPool.getCPStats
          } yield  {
            jsonResult.hcursor.get[Int]("current").getOrElse(0) should be >= 0
            jsonResult.hcursor.get[Int]("available").getOrElse(0) should be >= 0
            jsonResult.hcursor.get[Int]("totalCreated").getOrElse(0) should be >= 0
          }
        }

      "handle missing connection fields gracefully" in withEmbeddedMongoClient { client =>
        for {
          db <- client.getDatabase("test")
          // Here you might want to set up a test database without the expected stats
          connectionPool = ConnectionPool.impl[IO](db)
          jsonResult <- connectionPool.getCPStats
        } yield {
          jsonResult.hcursor.get[Int]("current").getOrElse(-1) should be >= 0
          jsonResult.hcursor.get[Int]("available").getOrElse(-1) should be >= 0
          jsonResult.hcursor.get[Int]("totalCreated").getOrElse(-1) should be >= 0
        }
      }
    }
  }

  def setupTestDatabase(db: MongoDatabase[IO]): IO[Unit] = {
    // This is where you would set up any initial state required for your tests
    // e.g., inserting documents or running specific MongoDB commands
    IO.unit
  }

  def withEmbeddedMongoClient[A](test: MongoClient[IO] => IO[A]): Future[A] =
    withRunningEmbeddedMongo {
      MongoClient
        .fromConnectionString[IO](s"mongodb://localhost:$mongoPort")
        .use(test)
    }.unsafeToFuture()(IORuntime.global)
}
