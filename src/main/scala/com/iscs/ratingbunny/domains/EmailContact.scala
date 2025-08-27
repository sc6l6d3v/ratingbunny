package com.iscs.ratingbunny.domains

import cats.effect.*
import cats.implicits.*
import com.typesafe.scalalogging.Logger
import mongo4cats.bson.syntax.*
import mongo4cats.bson.{BsonValue, Document}
import mongo4cats.collection.MongoCollection
import org.bson.conversions.{Bson => mbson}
import org.mongodb.scala.model.*
import org.mongodb.scala.result.UpdateResult
import java.time.Instant
import scala.concurrent.duration.FiniteDuration
import scala.util.matching.Regex

trait EmailContact[F[_]]:
  def saveEmail(name: String, email: String, subject: String, msg: String): F[String]

final case class Email(name: String, email: String, subject: String, msg: String):
  override def toString: String = List(
    s"name=$name",
    s"email=$email",
    s"subject=$subject",
    s"msg=$msg"
  ).mkString("| ")

class EmailContactImpl[F[_]: MonadCancelThrow: Sync](
    emailFx: MongoCollection[F, Document]
) extends EmailContact[F]:
  private val L = Logger[this.type]

  private val namePattern       = "[0-9a-zA-Z' ]+".r
  private val emailPattern      = "^(.+)@(\\S+)$".r
  private val maxValLen         = 128
  private val maxMsgLen         = 4000
  private val fieldCreationDate = "creationDate"
  private val fieldLastModified = "lastModified"
  private val fieldName         = "name"
  private val fieldSubject      = "subject"
  private val field_id          = "_id"
  private val fieldMsg          = "msg"

  private def checkVal(value: String, pattern: Regex): F[Boolean] =
    for
      truncValue <- Sync[F].delay(value.take(maxValLen))
      isValid    <- Sync[F].delay(pattern.matches(truncValue))
    yield isValid

  private def validate(name: String, email: String): F[Boolean] =
    for
      isValidName  <- checkVal(name, namePattern)
      isValidEmail <- checkVal(email, emailPattern)
    yield isValidName && isValidEmail

  def makeDoc(name: String, email: String, subject: String, msg: String): F[Document] =
    for doc <- Sync[F].delay(
        Document(
          fieldName    := name,
          fieldSubject := subject,
          field_id     := email,
          fieldMsg     := msg
        )
      )
    yield doc

  def makeUpdateDoc(doc: Document): F[mbson] =
    for updateDoc <- Sync[F].delay(
        Updates.combine(
          Document("$set" := doc),
          Updates.setOnInsert(fieldCreationDate, BsonValue.BDateTime(Instant.now)),
          Updates.currentDate(fieldLastModified)
        )
      )
    yield updateDoc

  private def updateMsg(name: String, email: String, subject: String, msg: String): F[String] =
    for
      asDoc     <- makeDoc(name, email, subject, msg)
      updateDoc <- makeUpdateDoc(asDoc)
      result <- Clock[F].timed(
        emailFx.updateOne(
          Filters.eq(field_id, email),
          updateDoc,
          UpdateOptions().upsert(true)
        )
      )
      (updateTime: FiniteDuration, updateResult: UpdateResult) = result
      _ <- Sync[F].delay(L.info(s"update email doc in {} ms", updateTime.toMillis))
      emailResponse <- Sync[F].delay:
        Option(updateResult.getUpsertedId)
          .map(_.asString().getValue)
          .getOrElse(email)
    yield emailResponse

  override def saveEmail(name: String, email: String, subject: String, msg: String): F[String] =
    val truncSubject = subject.take(maxValLen)
    val truncMsg     = msg.take(maxMsgLen)

    validate(name, email).flatMap: isValid =>
      val emailAction: F[String] =
        if (isValid) updateMsg(name, email, truncSubject, truncMsg)
        else Sync[F].delay(s"Invalid: $email")

      Clock[F]
        .timed(emailAction)
        .flatMap:
          case (totEmailTime, emailJson) =>
            Sync[F].delay(L.info(s"total email time {} ms", totEmailTime.toMillis)).as(emailJson)
