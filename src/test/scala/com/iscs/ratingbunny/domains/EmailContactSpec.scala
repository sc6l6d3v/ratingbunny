package com.iscs.ratingbunny.domains

import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.iscs.mail.EmailAttachment
import com.iscs.ratingbunny.testkit.MockEmailService
import com.typesafe.scalalogging.Logger
import jakarta.mail.SendFailedException
import mongo4cats.bson.Document
import mongo4cats.bson.syntax.*
import mongo4cats.client.MongoClient
import mongo4cats.collection.MongoCollection
import mongo4cats.database.MongoDatabase
import mongo4cats.embedded.EmbeddedMongo
import mongo4cats.models.collection.UpdateOptions
import org.mongodb.scala.model.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import scala.concurrent.Future

class EmailContactSpec extends AsyncWordSpec with Matchers with EmbeddedMongo:

  private val L = Logger[this.type]

  override val mongoPort: Int = 12348
  private val field_id        = "_id"

  /** Use mock email service to capture sends */
  private val mockEmailService = new MockEmailService[IO]()

  private val emailDoc = Document(
    "name"    := "John Doe",
    "_id"     := "john.doe@example.com",
    "subject" := "Test Subject",
    "msg"     := "This is a test message."
  )

  "embedded MongoDB" when:
    "sending email" should:
      "saveEmail should return email when validation succeeds and update is successful" in:
        withEmbeddedMongoClient: client =>
          for
            db      <- setupTestDatabase("test", client)
            emailFx <- setupTestCollection(db, "mock")
            _ <- emailFx
              .insertOne(emailDoc)
              .attempt
              .flatMap:
                case Left(e) =>
                  IO(L.error(s"failed to insert $emailDoc")) >> IO.raiseError(e)
                case Right(_) =>
                  IO(L.info(s"succeeded insert $emailDoc"))
            emailContact = new EmailContactImpl[IO](emailFx, mockEmailService)
            name         = "John Doe"
            email        = "john.doe@example.com"
            subject      = "Test Subject"
            msg          = "This is a test message."
            result    <- emailContact.saveEmail(name, email, subject, msg)
            asDoc     <- emailContact.makeDoc(name, email, subject, msg)
            updateDoc <- emailContact.makeUpdateDoc(asDoc)
            emailUpdateResult <- emailFx.updateOne(
              Filters.eq(field_id, email),
              updateDoc,
              UpdateOptions().upsert(true)
            )
          yield
            emailUpdateResult.getModifiedCount shouldBe 1L
            result should startWith("mock-message-id")
            result should include(email)

      "saveEmail should return 'Invalid' message when validation fails" in:
        withEmbeddedMongoClient: client =>
          for
            db             <- setupTestDatabase("test", client)
            mockCollection <- setupTestCollection(db, "mock")
            emailContact = new EmailContactImpl[IO](mockCollection, mockEmailService)
            name         = "John Doe"
            invalidEmail = "john.doe@"
            subject      = "Test Subject"
            msg          = "This is a test message."
            result <- emailContact.saveEmail(name, invalidEmail, subject, msg)
          yield result shouldBe s"Invalid: $invalidEmail"

      "updateMsg should correctly update and return the email address" in:
        withEmbeddedMongoClient: client =>
          for
            db      <- setupTestDatabase("test", client)
            emailFx <- setupTestCollection(db, "mock")
            emailContact = new EmailContactImpl[IO](emailFx, mockEmailService)
            name         = "Jane Doe"
            email        = "jane.doe@example.com"
            subject      = "Another Test"
            msg          = "This is another test message."
            result <- emailContact.saveEmail(name, email, subject, msg)
          yield
            result should startWith("mock-message-id")
            result should include(email)

      "sendEmail should surface failure when underlying service errors" in:
        withEmbeddedMongoClient: client =>
          for
            db      <- setupTestDatabase("test", client)
            emailFx <- setupTestCollection(db, "mock")
            failingService = new MockEmailService[IO]():
              override def sendEmail(
                  to: String,
                  subject: String,
                  textBody: String,
                  htmlBody: String,
                  attachments: List[EmailAttachment[IO]]
              ): IO[NonEmptyList[String]] =
                IO.raiseError(new SendFailedException("bad address"))
              override def sendEmail(
                  to: String,
                  subject: String,
                  textBody: String,
                  htmlBody: String
              ): IO[NonEmptyList[String]] =
                sendEmail(to, subject, textBody, htmlBody, Nil)
              override def sendEmailToMultiple(
                  recipients: List[String],
                  subject: String,
                  textBody: String,
                  htmlBody: String,
                  attachments: List[EmailAttachment[IO]]
              ): IO[List[NonEmptyList[String]]] =
                IO.raiseError(new SendFailedException("bad address"))
              override def sendEmailToMultiple(
                  recipients: List[String],
                  subject: String,
                  textBody: String,
                  htmlBody: String
              ): IO[List[NonEmptyList[String]]] =
                sendEmailToMultiple(recipients, subject, textBody, htmlBody, Nil)
              override def send(mail: emil.Mail[IO]): IO[NonEmptyList[String]] =
                IO.raiseError(new SendFailedException("bad address"))
            emailContact = new EmailContactImpl[IO](emailFx, failingService)
            result <- emailContact
              .saveEmail("John Doe", "john.doe@example.com", "Test", "msg")
              .attempt
          yield result match
            case Left(_: SendFailedException) => succeed
            case other                        => fail(s"expected SendFailedException but got $other")

  def setupTestDatabase(name: String, client: MongoClient[IO]): IO[MongoDatabase[IO]] =
    client.getDatabase(name)

  def setupTestCollection(db: MongoDatabase[IO], name: String): IO[MongoCollection[IO, Document]] =
    db.getCollection(name)

  def withEmbeddedMongoClient[A](test: MongoClient[IO] => IO[A]): Future[A] =
    withRunningEmbeddedMongo:
      MongoClient
        .fromConnectionString[IO](s"mongodb://localhost:$mongoPort")
        .use(test)
    .unsafeToFuture()(IORuntime.global)
