package com.iscs.ratingslave.domains

import cats.effect._
import cats.implicits._
import com.typesafe.scalalogging.Logger
import mongo4cats.bson.syntax._
import mongo4cats.bson.{BsonValue, Document}
import mongo4cats.circe._
import mongo4cats.collection.MongoCollection
import org.bson.conversions.{Bson => mbson}
import org.mongodb.scala.model._
import java.time.Instant
import scala.language.implicitConversions
import scala.util.matching.Regex

trait EmailContact[F[_]] {
  def saveEmail(name: String, email: String, subject: String, msg: String): F[String]
}

object EmailContact {
  private val L = Logger[this.type]

  def apply[F[_]](implicit ev: EmailContact[F]): EmailContact[F] = ev

  final case class Email(name: String, email: String, subject: String, msg: String) {
    override def toString: String = List(
      s"name=$name",
      s"email=$email",
      s"subject=$subject",
      s"msg=$msg"
    ).mkString("| ")
  }

  def impl[F[_] : Sync](emailFx: MongoCollection[F, Document]): EmailContact[F] =
    new EmailContact[F] {

      private val namePattern = "[0-9a-zA-Z' ]+".r
      private val emailPattern = "^(.+)@(\\S+)$".r
      private val maxValLen = 128
      private val maxMsgLen = 4000
      private val fieldCreationDate = "creationDate"
      private val fieldLastModified = "lastModified"
      private val fieldName = "name"
      private val fieldSubject = "subject"
      private val field_id = "_id"
      private val fieldMsg = "msg"

      private def checkVal(value: String, pattern: Regex): F[Boolean] = for {
        truncValue <- Sync[F].delay(value.take(maxValLen))
        isValid <- Sync[F].delay(pattern.matches(truncValue))
      } yield isValid

      def validate(name: String, email: String): F[Boolean] = for {
        isValidName <- checkVal(name, namePattern)
        isValidEmail <- checkVal(email, emailPattern)
      } yield isValidName && isValidEmail

      def makeDoc(name: String, email: String, subject: String, msg: String): F[Document] = for {
        doc <- Sync[F].delay(Document(
          fieldName := name,
          fieldSubject := subject,
          field_id := email,
          fieldMsg := msg,
        ))
      } yield doc

      def makeUpdateDoc(doc: Document): F[mbson] = for {
        updateDoc <- Sync[F].delay(Updates.combine(Document("$set" := doc),
          Updates.setOnInsert(fieldCreationDate, BsonValue.BDateTime(Instant.now)),
          Updates.currentDate(fieldLastModified)
        ))
      } yield updateDoc

      def updateMsg(name: String, email: String, subject: String, msg: String): F[String] = for {
        asDoc <- makeDoc(name, email, subject, msg)
        updateDoc <- makeUpdateDoc(asDoc)
        (updateTime, updateResult) <- Clock[F].timed(emailFx.updateOne(
          Filters.eq(field_id, email),
          updateDoc,
          UpdateOptions().upsert(true)))
        _ <- Sync[F].delay(L.info(s"update email doc in {} ms", updateTime.toMillis))
        emailResponse <- Sync[F].delay {
          if (updateResult.getUpsertedId == null)
            email
          else
            updateResult.getUpsertedId.asString().getValue
        }
      } yield emailResponse

      override def saveEmail(name: String, email: String, subject: String, msg: String): F[String] = for {
        truncSubject <- Sync[F].delay(subject.take(maxValLen))
        truncMsg <- Sync[F].delay(msg.take(maxMsgLen))
        isValid <- validate(name, email)
        (totEmailTime, emailJson) <- Clock[F].timed {
          if (isValid) updateMsg(name, email, truncSubject, truncMsg)
          else Sync[F].delay(s"Invalid: $email")
        }
        _ <- Sync[F].delay(L.info(s"total email time {} ms", totEmailTime.toMillis))
      } yield emailJson
    }
}
