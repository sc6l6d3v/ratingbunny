package com.iscs.ratingbunny.domains

import cats.effect.*
import cats.implicits.*
import com.typesafe.scalalogging.Logger
import com.iscs.ratingbunny.messaging.EmailJob
import mongo4cats.bson.syntax.*
import mongo4cats.bson.{BsonValue, Document}
import mongo4cats.collection.MongoCollection

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
    emailFx: MongoCollection[F, Document],
    publishEmailJob: EmailJob => F[Unit]
) extends EmailContact[F]:
  private val L = Logger[this.type]

  private val namePattern       = "[0-9a-zA-Z' ]+".r
  private val emailPattern      = "^(.+)@(\\S+)$".r
  private val maxValLen         = 128
  private val maxMsgLen         = 4000
  private val fieldCreationDate = "creationDate"
  private val fieldLastModified = "lastModified"
  private val fieldName         = "name"
  private val fieldEmail        = "email"
  private val fieldSubject      = "subject"
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
          fieldEmail   := email,
          fieldSubject := subject,
          fieldMsg     := msg,
          fieldCreationDate := BsonValue.BDateTime(Instant.now),
          fieldLastModified := BsonValue.BDateTime(Instant.now)
        )
      )
    yield doc

  private def insertMsg(name: String, email: String, subject: String, msg: String): F[String] =
    for
      asDoc     <- makeDoc(name, email, subject, msg)
      result <- Clock[F].timed(
        emailFx.insertOne(asDoc)
      )
      (insertTime: FiniteDuration, insertResult) = result
      _ <- Sync[F].delay(L.info(s"insert email doc in {} ms", insertTime.toMillis))
      insertedId <- Sync[F].delay:
        Option(insertResult.getInsertedId)
          .map(_.asObjectId().getValue.toHexString)
          .getOrElse("")
    yield insertedId

  override def saveEmail(name: String, email: String, subject: String, msg: String): F[String] =
    val truncSubject = subject.take(maxValLen)
    val truncMsg     = msg.take(maxMsgLen)

    validate(name, email).flatMap: isValid =>
      val emailAction: F[String] =
        if (isValid)
          insertMsg(name, email, truncSubject, truncMsg).flatTap: correlationId =>
            publishEmailJob(
              EmailJob(
                EmailJob.KindContact,
                email,
                correlationId = Option.when(correlationId.nonEmpty)(correlationId)
              )
            )
        else Sync[F].delay(s"Invalid: $email")

      Clock[F]
        .timed(emailAction)
        .flatMap:
          case (totEmailTime, _) =>
            Sync[F].delay(L.info(s"total email time {} ms", totEmailTime.toMillis)).as(email)
