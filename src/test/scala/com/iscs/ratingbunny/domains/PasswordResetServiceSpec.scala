package com.iscs.ratingbunny.domains

import cats.effect.IO
import cats.implicits.*
import com.iscs.ratingbunny.testkit.{MockEmailService, TestHasher}
import munit.CatsEffectSuite
import mongo4cats.circe.*
import mongo4cats.client.MongoClient
import mongo4cats.collection.MongoCollection
import mongo4cats.database.MongoDatabase
import mongo4cats.embedded.EmbeddedMongo
import mongo4cats.bson.syntax.*
import org.mongodb.scala.model.Filters

import java.time.Instant
import scala.concurrent.duration.*

class PasswordResetServiceSpec extends CatsEffectSuite with EmbeddedMongo:

  override val mongoPort: Int           = 12367
  override def munitIOTimeout: Duration = 2.minutes

  private def withMongo[A](f: MongoDatabase[IO] => IO[A]) =
    withRunningEmbeddedMongo:
      MongoClient
        .fromConnectionString[IO](s"mongodb://localhost:$mongoPort")
        .use(_.getDatabase("test").flatMap(f))
    .unsafeToFuture()

  private def insertUser(users: MongoCollection[IO, UserDoc], email: String): IO[Unit] =
    TestHasher
      .make[IO]
      .hash("Passw0rd!")
      .flatMap: pwHash =>
        users.insertOne(
          UserDoc(
            email = email,
            email_norm = email.toLowerCase,
            passwordHash = pwHash,
            userid = "u1",
            plan = Plan.Free,
            status = SubscriptionStatus.Active,
            displayName = None,
            createdAt = Instant.now(),
            emailVerified = true
          )
        ).void

  private class RecordingTokenIssuer extends TokenIssuer[IO]:
    var revoked: List[String] = Nil
    def issue(u: UserDoc)                          = IO.pure(TokenPair("a", "r"))
    def issueGuest(uid: String)                    = IO.pure(TokenPair("a", "r"))
    def rotate(r: String)                          = IO.pure(None)
    def revoke(r: String)                          = IO.unit
    def revokeUser(uid: String): IO[Unit]          = IO { revoked = uid :: revoked }

  test("requestReset stores hashed token and dispatches email"):
    withMongo: db =>
      for
        users   <- db.getCollectionWithCodec[UserDoc]("users")
        tokens  <- db.getCollectionWithCodec[PasswordResetTokenDoc]("password_reset_tokens")
        _       <- insertUser(users, "user@example.com")
        mailer   = new MockEmailService[IO]()
        issuer   = new RecordingTokenIssuer
        service  = PasswordResetServiceImpl.make(users, tokens, TestHasher.make[IO], mailer, issuer, "https://app.example.com")
        _       <- service.requestReset(PasswordResetRequest("user@example.com"))
        saved   <- tokens.find.all
      yield
        assertEquals(saved.size, 1)
        assert(saved.head.tokenHash.nonEmpty)
        assert(saved.head.expiresAt.isAfter(saved.head.createdAt))
        assertEquals(mailer.getSentEmails.size, 1)
        assert(mailer.getSentEmails.head.textBody.contains("/reset-password?token="))

  test("confirmReset updates password, marks token used, and revokes sessions"):
    withMongo: db =>
      for
        users   <- db.getCollectionWithCodec[UserDoc]("users")
        tokens  <- db.getCollectionWithCodec[PasswordResetTokenDoc]("password_reset_tokens")
        _       <- insertUser(users, "user@example.com")
        mailer   = new MockEmailService[IO]()
        issuer   = new RecordingTokenIssuer
        service  = PasswordResetServiceImpl.make(users, tokens, TestHasher.make[IO], mailer, issuer, "https://app.example.com")
        _       <- service.requestReset(PasswordResetRequest("user@example.com"))
        email    = mailer.getSentEmails.head
        token    = email.textBody.split("token=").last
        result  <- service.confirmReset(PasswordResetConfirmRequest(token, "N3wPassw0rd!"))
        userDoc <- users.find(Filters.eq("userid", "u1")).first
        saved   <- tokens.find.all
      yield
        assertEquals(result, Right(()))
        assert(saved.head.usedAt.nonEmpty)
        assertEquals(issuer.revoked.contains("u1"), true)
        assertEquals(userDoc.exists(_.passwordHash == "!dr0wssaPw3N"), true) // reversed by TestHasher

  test("confirmReset rejects unknown tokens"):
    withMongo: db =>
      for
        users   <- db.getCollectionWithCodec[UserDoc]("users")
        tokens  <- db.getCollectionWithCodec[PasswordResetTokenDoc]("password_reset_tokens")
        _       <- insertUser(users, "user@example.com")
        mailer   = new MockEmailService[IO]()
        issuer   = new RecordingTokenIssuer
        service  = PasswordResetServiceImpl.make(users, tokens, TestHasher.make[IO], mailer, issuer, "https://app.example.com")
        res     <- service.confirmReset(PasswordResetConfirmRequest("bogus", "SomePass1!"))
      yield assertEquals(res, Left(PasswordResetError.InvalidOrExpired))
