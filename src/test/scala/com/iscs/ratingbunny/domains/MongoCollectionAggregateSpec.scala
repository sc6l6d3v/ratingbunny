package com.iscs.ratingbunny.domains

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import mongo4cats.bson.Document
import mongo4cats.bson.syntax.*
import mongo4cats.client.MongoClient
import mongo4cats.database.MongoDatabase
import mongo4cats.embedded.EmbeddedMongo
import mongo4cats.models.collection.UnwindOptions
import mongo4cats.operations.*
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import scala.concurrent.Future
class MongoCollectionAggregateSpec extends AsyncWordSpec with Matchers with EmbeddedMongo {
  override val mongoPort = 12350

  "A MongoCollection" when {
    "aggregate" should {
      "join data from 2 collections" in withEmbeddedMongoDatabase { db =>
        val result = for {
          accs <- db.getCollection("accounts")
          res <- accs
            .aggregate[Document] {
              Aggregate
                .matchBy(Filter.eq("currency", TestData.USD))
                .lookup("transactions", "_id", "account", "transactions")
                .project(
                  Projection
                    .include(List("transactions", "name", "currency"))
                    .computed("totalAmount", Document("$sum" := "$transactions.amount"))
                )
            }
            .first
        } yield res

        result.map { acc =>
          acc.get.getList("transactions").get must have size 250
          acc.get.getInt("totalAmount").get must be > 0
        }
      }

      "group by field" in withEmbeddedMongoDatabase { db =>
        val result = for {
          transactions <- db.getCollection("transactions")
          accumulator = Accumulator
            .sum("count", 1)
            .sum("totalAmount", "$amount")
            .first("categoryId", "$category._id")
          res <- transactions
            .aggregate[Document] {
              Aggregate
                .group("$category", accumulator)
                .lookup("categories", "categoryId", "_id", "category")
                .sort(Sort.desc("count"))
            }
            .all
        } yield res

        result.map { cats =>
          cats must have size 10
          val counts = cats.flatMap(_.getInt("count")).toList
          counts.reverse mustBe sorted
          counts.sum mustBe 250
        }
      }

      "explain the pipeline in a form of a document" in withEmbeddedMongoDatabase { db =>
        val result = for {
          accs <- db.getCollection("accounts")
          res <- accs
            .aggregate[Document] {
              Aggregate
                .matchBy(Filter.eq("currency", TestData.USD))
                .lookup("transactions", "_id", "account", "transactions")
                .sort(Sort.asc("name"))
                .project(Projection.excludeId)
            }
            .explain
        } yield res

        result.map { expl =>
          expl.getDouble("ok") mustBe Some(1.0)
          expl.getNestedAs[Int]("serverInfo.port") mustBe Some(mongoPort)
          expl.getList("stages").get must have size 2
        }
      }

      "processes multiple aggregation pipelines within a single stage on the same set of input documents" in {
        withEmbeddedMongoDatabase { db =>
          val result = for {
            txs <- db.getCollection("transactions")
            accumulator = Accumulator
              .sum("count", 1)
              .sum("totalAmount", "$amount")
            res <- txs
              .aggregate[Document] {
                Aggregate.facet(
                  Aggregate.Facet("transactionsByCategory", Aggregate.group("$category", accumulator)),
                  Aggregate.Facet("transactionsByAccount", Aggregate.group("$account", accumulator))
                )
              }
              .first
          } yield res

          result.map(_.get).map { res =>
            res.getList("transactionsByCategory").get must have size 10
            res.getList("transactionsByAccount").get must have size 1
          }
        }
      }

      "collect all unique account currencies" in {
        withEmbeddedMongoDatabase { db =>
          val result = for {
            accs <- db.getCollection("accounts")
            res <- accs
              .aggregate[Document](
                Aggregate
                  .project(Projection.excludeId)
                  .unwind("$name", UnwindOptions().preserveNullAndEmptyArrays(false))
                  .group("$_id", Accumulator.addToSet("uniqueNames", "$name"))
                  .project(Projection.slice("uniqueNames", 1, 1))
              )
              .all
          } yield res

          result.map { res =>
            val accounts = res.flatMap(_.getAs[List[String]]("uniqueNames")).flatten
            accounts must have size 1
            accounts must contain oneOf ("usd-acc", "eur-acc", "gbp-acc")
          }
        }
      }

      "using first, return none if no result is found" in {
        withEmbeddedMongoDatabase { db =>
          val result = for {
            accs <- db.getCollection("accounts")
            res <- accs
              .aggregate[Document](
                Aggregate
                  .matchBy(Filter.eq("currency", TestData.LVL))
              )
              .first
          } yield res

          result.map { res =>
            res mustBe empty
          }
        }
      }
    }
  }

  def withEmbeddedMongoDatabase[A](test: MongoDatabase[IO] => IO[A]): Future[A] =
    withRunningEmbeddedMongo {
      MongoClient
        .fromConnectionString[IO](s"mongodb://localhost:$mongoPort")
        .use { client =>
          for {
            db  <- client.getDatabase("db")
            _   <- db.getCollection("accounts").flatMap(_.insertMany(TestData.accounts))
            _   <- db.getCollection("categories").flatMap(_.insertMany(TestData.categories))
            _   <- db.getCollection("transactions").flatMap(_.insertMany(TestData.transactions(250)))
            res <- test(db)
          } yield res
        }
    }.unsafeToFuture()(IORuntime.global)
}
