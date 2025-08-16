package com.iscs.ratingbunny.repos

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.iscs.ratingbunny.domains.QuerySetup
import com.iscs.ratingbunny.model.Requests.ReqParams
import mongo4cats.client.MongoClient
import mongo4cats.embedded.EmbeddedMongo
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import scala.concurrent.Future

class HistoryRepoSpec extends AsyncWordSpec with Matchers with EmbeddedMongo with QuerySetup {

  override val mongoPort: Int = 12349

  "HistoryRepo" when {
    "ensureIndexes is invoked" should {
      "create compound and unique indexes only once" in withEmbeddedMongoClient { client =>
        for {
          db   <- client.getDatabase("test")
          repo <- HistoryRepo.make[IO](db)
          // first run: indexes should exist now
          idxDocs <- repo.coll.listIndexes
          idx1 = idxDocs.flatMap(_.getString("name").toList)
          // second run: idempotent
          _        <- repo.ensureIndexes
          idx2Docs <- repo.coll.listIndexes
          idx2 = idx2Docs.flatMap(_.getString("name").toList)
        } yield {
          idx1.toSet should contain allOf ("_id_", "uid_date_idx", "uid_sig_unique_idx")
          idx2 shouldEqual idx1
        }
      }
    }

    "log" should {
      "insert on first call and increment hits on duplicate" in withEmbeddedMongoClient { client =>
        val params = ReqParams(
          genre = Some(List("Drama")),
          year = Some(List(2022, 2025)),
          votes = Some(200000),
          titleType = Some(List("movie")),
          isAdult = Some(false),
          sortType = Some("title")
        )
        for {
          db   <- client.getDatabase("test")
          repo <- HistoryRepo.make[IO](db)
          _    <- repo.log("user-123", params) // insert
          _    <- repo.log("user-123", params) // upsert, hits +1
          doc  <- repo.coll.find(feq("userId", "user-123")).first
        } yield doc.get.getInt("hits").get shouldBe 2
      }

      "allow identical searches from different users" in withEmbeddedMongoClient { client =>
        val params = ReqParams(
          genre = Some(List("Drama")),
          year = Some(List(2022, 2025)),
          votes = Some(200000),
          titleType = Some(List("movie")),
          isAdult = Some(false),
          sortType = Some("title")
        )
        for {
          db   <- client.getDatabase("test")
          repo <- HistoryRepo.make[IO](db)
          _    <- repo.log("user-1", params)
          _    <- repo.log("user-2", params)
          c1   <- repo.coll.count(feq("userId", "user-1"))
          c2   <- repo.coll.count(feq("userId", "user-2"))
        } yield {
          c1 shouldBe 1
          c2 shouldBe 1
        }
      }
    }
  }

  // helper identical to ConnectionPoolTest
  private def withEmbeddedMongoClient[A](test: MongoClient[IO] => IO[A]): Future[A] =
    withRunningEmbeddedMongo {
      MongoClient
        .fromConnectionString[IO](s"mongodb://localhost:$mongoPort")
        .use(test)
    }.unsafeToFuture()(IORuntime.global)
}
