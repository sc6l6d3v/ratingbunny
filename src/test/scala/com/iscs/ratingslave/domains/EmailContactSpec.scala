package com.iscs.ratingslave.domains

/*
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.iscs.ratingslave.domains.EmailContact
import mongo4cats.collection.MongoCollection
import mongo4cats.bson.Document
import org.bson.conversions.Bson
import mongo4cats.models.collection.UpdateOptions
import org.mongodb.scala.result.UpdateResult
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.compiletime.uninitialized

class EmailContactSpec extends AnyFunSuite with Matchers with MockFactory with BeforeAndAfterEach {

  var mockCollection: MongoCollection[IO, Document] = uninitialized

  override def beforeEach(): Unit = {
    mockCollection = mock[MongoCollection[IO, Document]]
  }

  test("saveEmail should return email when validation succeeds and update is successful") {
    val emailContact = new EmailContactImpl[IO](mockCollection)
    val name = "John Doe"
    val email = "john.doe@example.com"
    val subject = "Test Subject"
    val msg = "This is a test message."

    val updateResult = stub[UpdateResult]
    (() => updateResult.getUpsertedId).when().returns(null)

    (mockCollection.updateOne(_: Bson, _: Bson, _: UpdateOptions))
      .expects(*, *, *).returns(IO.pure(updateResult))

    val result = emailContact.saveEmail(name, email, subject, msg).unsafeRunSync()

    result shouldBe email
  }

  test("saveEmail should return 'Invalid' message when validation fails") {
    val emailContact = new EmailContactImpl[IO](mockCollection)
    val name = "John Doe"
    val invalidEmail = "john.doe@"
    val subject = "Test Subject"
    val msg = "This is a test message."

    val result = emailContact.saveEmail(name, invalidEmail, subject, msg).unsafeRunSync()

    result shouldBe s"Invalid: $invalidEmail"
  }

  test("updateMsg should correctly update and return the email address") {
    val emailContact = new EmailContactImpl[IO](mockCollection)
    val name = "Jane Doe"
    val email = "jane.doe@example.com"
    val subject = "Another Test"
    val msg = "This is another test message."

    val updateResult = stub[UpdateResult]
    (() => updateResult.getUpsertedId).when().returns(null)

    (mockCollection.updateOne(_: Bson, _: Bson, _: UpdateOptions))
      .expects(*, *, *).returns(IO.pure(updateResult))

    val result = emailContact.saveEmail(name, email, subject, msg).unsafeRunSync()

    result `shouldBe` email
  }
}
*/
