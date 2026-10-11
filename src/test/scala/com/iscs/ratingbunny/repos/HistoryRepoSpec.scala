package com.iscs.ratingbunny.repos

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.iscs.ratingbunny.domains.QuerySetup
import com.iscs.ratingbunny.model.Requests.ReqParams
import mongo4cats.bson.Document
import mongo4cats.bson.syntax.*
import mongo4cats.client.MongoClient
import mongo4cats.embedded.EmbeddedMongo
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import scala.concurrent.Future

class HistoryRepoSpec extends AsyncWordSpec with Matchers with EmbeddedMongo with QuerySetup:

  override val mongoPort: Int = 12349

  "HistoryRepo" when:
    "ensureIndexes is invoked" should:
      "create compound and unique indexes only once" in:
        withEmbeddedMongoClient: client =>
          for
            db   <- client.getDatabase("test")
            repo <- HistoryRepo.make[IO](db)
            // first run: indexes should exist now
            idxDocs <- repo.coll.listIndexes
            idx1 = idxDocs.flatMap(_.getString("name").toList)
            // second run: idempotent
            _        <- repo.ensureIndexes
            idx2Docs <- repo.coll.listIndexes
            idx2 = idx2Docs.flatMap(_.getString("name").toList)
          yield
            idx1.toSet should contain allOf ("_id_", "uid_date_idx", "uid_sig_unique_idx")
            idx2 shouldEqual idx1

    "log" should:
      "insert on first call and increment hits on duplicate" in:
        withEmbeddedMongoClient: client =>
          val params = ReqParams(
            genre = Some(List("Drama")),
            year = Some(List(2022, 2025)),
            votes = Some(200000),
            titleType = Some(List("movie")),
            isAdult = Some(false),
            sortType = Some("title")
          )
          for
            db   <- client.getDatabase("test")
            repo <- HistoryRepo.make[IO](db)
            _    <- repo.log("user-123", params) // insert
            _    <- repo.log("user-123", params) // upsert, hits +1
            doc  <- repo.coll.find(feq("userId", "user-123")).first
          yield doc.get.getInt("hits").get shouldBe 2

      "allow identical searches from different users" in:
        withEmbeddedMongoClient: client =>
          val params = ReqParams(
            genre = Some(List("Drama")),
            year = Some(List(2022, 2025)),
            votes = Some(200000),
            titleType = Some(List("movie")),
            isAdult = Some(false),
            sortType = Some("title")
          )
          for
            db   <- client.getDatabase("test")
            repo <- HistoryRepo.make[IO](db)
            _    <- repo.log("user-1", params)
            _    <- repo.log("user-2", params)
            c1   <- repo.coll.count(feq("userId", "user-1"))
            c2   <- repo.coll.count(feq("userId", "user-2"))
          yield
            c1 shouldBe 1
            c2 shouldBe 1

      "fold spellings of one search into one entry that keeps the latest spelling" in:
        withEmbeddedMongoClient: client =>
          val first = ReqParams(
            query = Some("Tenet"),
            genre = Some(List("Drama", "Comedy")),
            year = Some(List(2020, 2021)),
            sortType = Some("rating")
          )
          val second = ReqParams(
            query = Some("tenet "),
            genre = Some(List("Comedy", "Drama", "Comedy")),
            year = Some(List(2021, 2020)),
            sortType = Some("year")
          )
          for
            db   <- client.getDatabase("test")
            repo <- HistoryRepo.make[IO](db)
            _    <- repo.log("user-1", first)
            _    <- repo.log("user-1", second)
            docs <- repo.coll.find(feq("userId", "user-1")).all
          yield
            docs.size shouldBe 1
            docs.head.getInt("hits") shouldBe Some(2)
            val stored = docs.head.getDocument("params")
            stored.flatMap(_.getString("query")) shouldBe Some("tenet ")
            stored.flatMap(_.getString("sortType")) shouldBe Some("year")

      "propagate a write error that is not the duplicate-key race" in:
        withEmbeddedMongoClient: client =>
          val params = ReqParams(query = Some("tenet"))
          for
            db   <- client.getDatabase("test")
            repo <- HistoryRepo.make[IO](db)
            // an entry whose hits cannot be $inc-ed makes the upsert fail with a non-duplicate error
            _   <- repo.coll.insertOne(Document("userId" := "user-1", "sig" := repo.sigFor(params), "hits" := "many"))
            res <- repo.log("user-1", params).attempt
          yield res.isLeft shouldBe true

    "sigFor" should:
      "ignore spelling, ordering, duplicates and sortType" in:
        withEmbeddedMongoClient: client =>
          for
            db   <- client.getDatabase("test")
            repo <- HistoryRepo.make[IO](db)
          yield
            repo.sigFor(ReqParams(query = Some(" Tenet"))) shouldBe repo.sigFor(ReqParams(query = Some("tenet")))
            repo.sigFor(ReqParams(query = Some("  "))) shouldBe repo.sigFor(ReqParams())
            repo.sigFor(ReqParams(genre = Some(List("Drama", "Comedy")))) shouldBe
              repo.sigFor(ReqParams(genre = Some(List("Comedy", "Drama", "Drama"))))
            repo.sigFor(ReqParams(titleType = Some(List("tvSeries", "movie")))) shouldBe
              repo.sigFor(ReqParams(titleType = Some(List("movie", "tvSeries"))))
            repo.sigFor(ReqParams(genre = Some(Nil))) shouldBe repo.sigFor(ReqParams())
            repo.sigFor(ReqParams(year = Some(List(2021, 2020)))) shouldBe repo.sigFor(ReqParams(year = Some(List(2020, 2021))))
            repo.sigFor(ReqParams(year = Some(List(2020)))) shouldBe repo.sigFor(ReqParams(year = Some(List(2020, 2020))))
            repo.sigFor(ReqParams(votes = Some(10), sortType = Some("year"))) shouldBe
              repo.sigFor(ReqParams(votes = Some(10), sortType = Some("rating")))

      "keep different searches apart" in:
        withEmbeddedMongoClient: client =>
          for
            db   <- client.getDatabase("test")
            repo <- HistoryRepo.make[IO](db)
          yield
            val base = ReqParams(query = Some("tenet"), genre = Some(List("Drama")), year = Some(List(2020, 2021)))
            val variants = List(
              base.copy(query = Some("tenet 2")),
              base.copy(genre = Some(List("Comedy"))),
              base.copy(year = Some(List(2019, 2021))),
              base.copy(votes = Some(1000)),
              base.copy(isAdult = Some(true)),
              base.copy(searchType = Some("exact")),
              base.copy(lang = Some("en"))
            )
            (base :: variants).map(repo.sigFor).distinct.size shouldBe variants.size + 1

    "byParams" should:
      "find the entry for an equivalent spelling" in:
        withEmbeddedMongoClient: client =>
          for
            db    <- client.getDatabase("test")
            repo  <- HistoryRepo.make[IO](db)
            _     <- repo.log("user-1", ReqParams(query = Some("Tenet"), year = Some(List(2020, 2021))))
            found <- repo.byParams("user-1", ReqParams(query = Some(" TENET"), year = Some(List(2021, 2020)), sortType = Some("year")))
            other <- repo.byParams("user-2", ReqParams(query = Some("Tenet"), year = Some(List(2020, 2021))))
          yield
            found.isDefined shouldBe true
            other.isDefined shouldBe false

  // helper identical to ConnectionPoolTest
  private def withEmbeddedMongoClient[A](test: MongoClient[IO] => IO[A]): Future[A] =
    withRunningEmbeddedMongo:
      MongoClient
        .fromConnectionString[IO](s"mongodb://localhost:$mongoPort")
        .use(test)
    .unsafeToFuture()(IORuntime.global)
