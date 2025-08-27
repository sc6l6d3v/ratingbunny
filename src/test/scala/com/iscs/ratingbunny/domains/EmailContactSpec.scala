package com.iscs.ratingbunny.domains

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import mongo4cats.bson.Document
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

  override val mongoPort: Int = 12348
  private val field_id        = "_id"

  "embedded MongoDB" when:
    "sending email" should:
      "saveEmail should return email when validation succeeds and update is successful" in:
        withEmbeddedMongoClient: client =>
          for
            db      <- setupTestDatabase("test", client)
            emailFx <- setupTestCollection(db, "mock")
            emailContact = new EmailContactImpl[IO](emailFx)
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
            result shouldBe email

      "saveEmail should return 'Invalid' message when validation fails" in:
        withEmbeddedMongoClient: client =>
          for
            db             <- setupTestDatabase("test", client)
            mockCollection <- setupTestCollection(db, "mock")
            emailContact = new EmailContactImpl[IO](mockCollection)
            name         = "John Doe"
            invalidEmail = "john.doe@"
            subject      = "Test Subject"
            msg          = "This is a test message."
            result <- emailContact.saveEmail(name, invalidEmail, subject, msg)
          yield result shouldBe s"Invalid: $invalidEmail"

      "updateMsg should correctly update and return the email address" in:
        withEmbeddedMongoClient: client =>
          for
            db             <- setupTestDatabase("test", client)
            mockCollection <- setupTestCollection(db, "mock")
            emailContact = new EmailContactImpl[IO](mockCollection)
            name         = "Jane Doe"
            email        = "jane.doe@example.com"
            subject      = "Another Test"
            msg          = "This is another test message."
            result <- emailContact.saveEmail(name, email, subject, msg)
          yield result shouldBe email

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
