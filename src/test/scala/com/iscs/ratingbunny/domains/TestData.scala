package com.iscs.ratingbunny.domains

import mongo4cats.bson.{Document, ObjectId}
import mongo4cats.bson.syntax._

import java.time.Instant
import scala.util.Random

object TestData {
  implicit private val random: Random = Random

  val USD: Document = Document("symbol" := "$", "code" := "USD")
  val GBP: Document = Document("symbol" := "£", "code" := "GBP")
  val EUR: Document = Document("symbol" := "€", "code" := "EUR")
  val LVL: Document = Document("symbol" := "L", "code" := "LVL")

  val usdAccount: Document = Document("_id" := ObjectId.gen, "currency" := USD, "name" := "usd-acc")
  val gbpAccount: Document = Document("_id" := ObjectId.gen, "currency" := GBP, "name" := "gbp-acc")
  val eurAccount: Document = Document("_id" := ObjectId.gen, "currency" := EUR, "name" := "eur-acc")
  val lvlAccount: Document = Document("_id" := ObjectId.gen, "currency" := LVL, "name" := "lvl-acc")

  val accounts: Vector[Document]   = Vector(usdAccount, gbpAccount, eurAccount)
  val categories: Vector[Document] = categories(10)

  def transaction(account: Document): Document =
    Document(
      "_id"      := ObjectId.gen,
      "date"     := Instant.now().minusSeconds(random.nextInt(1000).toLong),
      "category" := categories.pickRand,
      "account"  := account.getObjectId("_id").get,
      "amount"   := random.nextInt(10000)
    )

  def transactions(n: Int, account: Document = usdAccount): Vector[Document] = (0 until n).map(_ => transaction(account)).toVector
  def categories(n: Int): Vector[Document] = (0 until n).map(i => Document("_id" := ObjectId.gen, "name" := s"cat-$i")).toVector

  implicit final private class SeqOps[A](private val seq: Seq[A]) extends AnyVal {
    def pickRand(implicit rnd: Random): A =
      seq(rnd.nextInt(seq.size))
  }
}
