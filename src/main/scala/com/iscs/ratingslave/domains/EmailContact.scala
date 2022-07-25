package com.iscs.ratingslave.domains

import cats.Applicative
import cats.effect.{Concurrent, Sync}
import cats.implicits._
import com.iscs.ratingslave.util.DbClient
import com.typesafe.scalalogging.Logger
import mongo4cats.bson.Document
import mongo4cats.collection.MongoCollection
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}
import zio.json.interop.http4s._
import zio.json.ast.Json
/*import io.circe._
import io.circe.generic.semiauto._
import io.circe.syntax._
import org.http4s.circe._ */
import org.http4s.{EntityDecoder, EntityEncoder}
import org.mongodb.scala.bson.{BsonDateTime, BsonString}
//import org.mongodb.scala.bson.Document
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.{eq => mdbeq}
import org.mongodb.scala.model._
import org.mongodb.scala.model.Updates._

import scala.language.implicitConversions
import scala.util.matching.Regex

trait EmailContact[F[_]] {
  def saveEmail(name: String, email: String, subject: String, msg: String): F[Json]
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
    implicit def emailEntityDecoder[F[_]: Applicative: Concurrent]: EntityDecoder[F, Email] = jsonOf
    implicit val emailEncoder: JsonEncoder[Email] = DeriveJsonEncoder.gen[Email]
    implicit def emailEntityEncoder[F[_]: Sync]: EntityEncoder[F, Email] = jsonEncoderOf[F, Email]
  }

    def impl[F[_]: Sync](emailFx: MongoCollection[F, Document]): EmailContact[F] =
      new EmailContact[F] {
        private val emailCollection = "email_contact"

    //    private val emailFx = dbClient.fxMap(emailCollection)

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
            fieldName -> BsonString(name),
            fieldSubject -> BsonString(subject),
            field_id -> BsonString(email),
            fieldMsg -> BsonString(msg),
          ))
        } yield doc

        def makeUpdateDoc(doc: Document): F[List[Bson]] = for {
          ts <- Sync[F].delay(BsonDateTime( new java.util.Date().getTime))
          tsdoc <- Sync[F].delay(setOnInsert(fieldCreationDate, ts))
          curdateDoc <- Sync[F].delay(currentDate(fieldLastModified))
          updateDoc <- Sync[F].delay(List(Document("$set" -> doc), curdateDoc, tsdoc))
        } yield updateDoc

        def updateMsg(name: String, email: String, subject: String, msg: String): F[Json] = for {
          emailWithTS <- withTimeStamp(name, email, subject, msg)
          updateDoc <- makeUpdateDoc(emailWithTS)
          updateResult <- emailFx.updateOne(
            mdbeq(field_id, email),
            combine(updateDoc:_*),
            UpdateOptions().upsert(true))
          emailAsId <- Sync[F].delay {
            if (updateResult.getUpsertedId == null)
              email
            else
              updateResult.getUpsertedId.asString().getValue
            }
          emailJson <- Sync[F].delay(JsonEncoder.string.toJsonAST(emailAsId).getOrElse(Json.Null))
        } yield emailJson

        override def saveEmail(name: String, email: String, subject: String, msg: String): F[Json] = for {
          truncSubject <- Sync[F].delay(subject.take(maxValLen))
          truncMsg <- Sync[F].delay(msg.take(maxMsgLen))
          isValid <- validate(name, email)
          emailJson <- if (isValid) updateMsg(name, email, truncSubject, truncMsg)
          else
            Sync[F].delay(JsonEncoder.string.toJsonAST(s"Invalid: $email").getOrElse(Json.Null))
          obj <- Sync[F].delay(Json.Obj(("email", emailJson)))
        } yield obj
      }
}
