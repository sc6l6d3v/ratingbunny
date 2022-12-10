package com.iscs.ratingslave.domains

import cats.effect.Sync
import cats.implicits._
import com.typesafe.scalalogging.Logger
import mongo4cats.bson.Document
import mongo4cats.bson.syntax._
import mongo4cats.collection.MongoCollection
import mongo4cats.collection.operations.{Filter, Update}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}
import org.mongodb.scala.model.Updates._
import org.mongodb.scala.model._
import scala.language.implicitConversions
import scala.util.matching.Regex

trait EmailContact[F[_]] {
  def saveEmail2(name: String, email: String, subject: String, msg: String): F[String]
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

  object Email {
    implicit val emailDecoder: JsonDecoder[Email] = DeriveJsonDecoder.gen[Email]
    implicit val emailEncoder: JsonEncoder[Email] = DeriveJsonEncoder.gen[Email]
  }

  def impl[F[_] : Sync](emailFx: MongoCollection[F, Document]): EmailContact[F] =
    new EmailContact[F] {
      private val emailCollection = "email_contact"

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

      def withTimeStamp(name: String, email: String, subject: String, msg: String): F[Document] = for {
        doc <- Sync[F].delay(Document(
          fieldName := name,
          fieldSubject := subject,
          field_id := email,
          fieldMsg := msg,
        ))
      } yield doc

      def makeUpdateDoc(doc: Document): F[Update] = for {
        ts <- Sync[F].delay(new java.util.Date().getTime)
        fieldCreationUpdate <- Sync[F].delay(Update.setOnInsert(fieldCreationDate, ts))
        fieldLastModifiedUpdate <- Sync[F].delay(Update.currentDate(fieldLastModified))
//        tsdoc <- Sync[F].delay(setOnInsert(fieldCreationDate, ts))
//        curdateDoc <- Sync[F].delay(currentDate(fieldLastModified))
        updateDoc <- Sync[F].delay{
          val comboUpdate = Seq(Update.set("$set",doc), fieldCreationUpdate, fieldLastModifiedUpdate).reduce(_ combinedWith _)
          comboUpdate
        }
      } yield updateDoc

      def updateMsg2(name: String, email: String, subject: String, msg: String): F[String] = for {
        emailWithTS <- withTimeStamp(name, email, subject, msg)
        updateDoc <- makeUpdateDoc(emailWithTS)
        updateResult <- emailFx.updateOne(
          Filter.eq(field_id, email),
          updateDoc,
          UpdateOptions().upsert(true))
        emailAsId <- Sync[F].delay {
          if (updateResult.getUpsertedId == null)
            email
          else
            updateResult.getUpsertedId.asString().getValue
        }
      } yield emailAsId

      override def saveEmail2(name: String, email: String, subject: String, msg: String): F[String] = for {
        truncSubject <- Sync[F].delay(subject.take(maxValLen))
        truncMsg <- Sync[F].delay(msg.take(maxMsgLen))
        isValid <- validate(name, email)
        emailJson <- if (isValid) updateMsg2(name, email, truncSubject, truncMsg)
        else
          Sync[F].delay(s"Invalid: $email")
      } yield emailJson
    }
}
