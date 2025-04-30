package com.iscs.ratingbunny.domains

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import mongo4cats.client.MongoClient
import mongo4cats.embedded.EmbeddedMongo
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import mongo4cats.client
import scala.concurrent.Future

class ConnectionPoolTest extends AsyncWordSpec with Matchers with EmbeddedMongo {

  override val mongoPort: Int = 12348

  "embedded MongoDB" when {
    "invoking getCPStats" should {
      "return connection pool stats as JSON" in withEmbeddedMongoClient { client =>
        for {
          db <- client.getDatabase("test")
          connectionPool = new ConnectionPoolImpl[IO](db)
          jsonResult <- connectionPool.getCPStats
        } yield {
          jsonResult.hcursor.get[Int]("current").getOrElse(0) should be >= 0
          jsonResult.hcursor.get[Int]("available").getOrElse(0) should be >= 0
          jsonResult.hcursor.get[Int]("totalCreated").getOrElse(0) should be >= 0
        }
      }

      "handle missing connection fields gracefully" in withEmbeddedMongoClient { client =>
        for {
          db <- client.getDatabase("test")
          connectionPool = new ConnectionPoolImpl[IO](db)
          jsonResult <- connectionPool.getCPStats
        } yield {
          jsonResult.hcursor.get[Int]("current").getOrElse(-1) should be >= 0
          jsonResult.hcursor.get[Int]("available").getOrElse(-1) should be >= 0
          jsonResult.hcursor.get[Int]("totalCreated").getOrElse(-1) should be >= 0
        }
      }
    }
  }

  def withEmbeddedMongoClient[A](test: MongoClient[IO] => IO[A]): Future[A] =
    withRunningEmbeddedMongo {
      MongoClient
        .fromConnectionString[IO](s"mongodb://localhost:$mongoPort")
        .use(test)
    }.unsafeToFuture()(IORuntime.global)
}
