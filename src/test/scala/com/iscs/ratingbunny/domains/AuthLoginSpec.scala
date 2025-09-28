package com.iscs.ratingbunny.domains

import cats.effect.*
import cats.implicits.*
import com.iscs.ratingbunny.testkit.TestHasher
import io.circe.generic.auto.*
import mongo4cats.circe.*
import mongo4cats.client.MongoClient
import mongo4cats.collection.MongoCollection
import mongo4cats.database.MongoDatabase
import mongo4cats.embedded.EmbeddedMongo
import mongo4cats.bson.{Document, ObjectId}
import mongo4cats.bson.syntax.*
import munit.CatsEffectSuite

import java.time.Instant
import scala.concurrent.Future
import scala.concurrent.duration.*

class AuthLoginSpec extends CatsEffectSuite with EmbeddedMongo:

  override val mongoPort: Int           = 12356
  override def munitIOTimeout: Duration = 2.minutes
  private val hasher                    = TestHasher.make[IO]
  private val stubToken: TokenIssuer[IO] =
    new TokenIssuer[IO]:
      private val tp                               = TokenPair("access", "refresh")
      def issue(u: UserDoc): IO[TokenPair]         = IO.pure(tp)
      def issueGuest(uid: String): IO[TokenPair]   = IO.pure(tp)
      def rotate(r: String): IO[Option[TokenPair]] = IO.pure(Some(tp))
      def revoke(r: String): IO[Unit]              = IO.unit

  // ── helpers ──────────────────────────────────────────────────
  private def withMongo[A](f: MongoDatabase[IO] => IO[A]): Future[A] =
    withRunningEmbeddedMongo:
      MongoClient
        .fromConnectionString[IO](s"mongodb://localhost:$mongoPort")
        .use(_.getDatabase("test").flatMap(f))
    .unsafeToFuture()

  private def insertUser(
      col: MongoCollection[IO, UserDoc],
      email: String,
      pwd: String,
      status: SubscriptionStatus = SubscriptionStatus.Active,
      verified: Boolean = true
  ) =
    hasher
      .hash(pwd)
      .flatMap: h =>
        col.insertOne(
          UserDoc(
            email = email,
            email_norm = email.trim.toLowerCase,
            passwordHash = h,
            userid = "u1",
            plan = Plan.Free,
            status = status,
            displayName = None,
            createdAt = Instant.now(),
            emailVerified = verified
          )
        )

  // ── tests ────────────────────────────────────────────────────
  test("login succeeds for correct credentials"):
    withMongo: db =>
      for
        users <- db.getCollectionWithCodec[UserDoc]("users")
        _     <- insertUser(users, "bob@example.com", "Passw0rd!", verified = true)
        svc = new AuthLoginImpl[IO](users, hasher, stubToken)
        res <- svc.login(LoginRequest("bob@example.com", "Passw0rd!"))
      yield assert(res.exists(_.userid == "u1"))

  test("login fails for wrong password"):
    withMongo: db =>
      for
        users <- db.getCollectionWithCodec[UserDoc]("users")
        _     <- insertUser(users, "bob@example.com", "Passw0rd!", verified = true)
        svc = new AuthLoginImpl[IO](users, hasher, stubToken)
        res <- svc.login(LoginRequest("bob@example.com", "wrong"))
      yield assertEquals(res, Left(LoginError.BadPassword))

  test("login fails for inactive status"):
    withMongo: db =>
      for
        users <- db.getCollectionWithCodec[UserDoc]("users")
        _     <- insertUser(users, "inactive@ex.com", "Passw0rd!", SubscriptionStatus.PastDue, verified = true)
        svc = new AuthLoginImpl[IO](users, hasher, stubToken)
        res <- svc.login(LoginRequest("inactive@ex.com", "Passw0rd!"))
      yield assertEquals(res, Left(LoginError.Inactive))

  test("login fails for unknown email"):
    withMongo: db =>
      for
        users <- db.getCollectionWithCodec[UserDoc]("users")
        svc = new AuthLoginImpl[IO](users, hasher, stubToken)
        res <- svc.login(LoginRequest("nobody@ex.com", "Passw0rd!"))
      yield assertEquals(res, Left(LoginError.UserNotFound))

  test("login fails for unverified email"):
    withMongo: db =>
      for
        users <- db.getCollectionWithCodec[UserDoc]("users")
        _     <- insertUser(users, "unver@ex.com", "Passw0rd!", verified = false)
        svc = new AuthLoginImpl[IO](users, hasher, stubToken)
        res <- svc.login(LoginRequest("unver@ex.com", "Passw0rd!"))
      yield assertEquals(res, Left(LoginError.Unverified))

  test("login treats missing emailVerified as unverified"):
    withMongo: db =>
      for
        raw <- db.getCollectionWithCodec[Document]("users")
        h   <- hasher.hash("Passw0rd!")
        _ <- raw.insertOne(
          Document(
            "_id"          := ObjectId.gen,
            "email"        := "legacy@ex.com",
            "email_norm"   := "legacy@ex.com",
            "passwordHash" := h,
            "userid"       := "u1",
            "plan"         := Plan.Free,
            "status"       := SubscriptionStatus.Active,
            "displayName"  := Option.empty[String],
            "prefs"        := List.empty[String],
            "createdAt"    := Instant.now()
          )
        )
        users <- db.getCollectionWithCodec[UserDoc]("users")
        svc = new AuthLoginImpl[IO](users, hasher, stubToken)
        res <- svc.login(LoginRequest("legacy@ex.com", "Passw0rd!"))
      yield assertEquals(res, Left(LoginError.Unverified))

  test("login succeeds with mixed-case email"):
    withMongo: db =>
      for
        users <- db.getCollectionWithCodec[UserDoc]("users")
        _     <- insertUser(users, "bob@example.com", "Passw0rd!", verified = true)
        svc = new AuthLoginImpl[IO](users, hasher, stubToken)
        res <- svc.login(LoginRequest("BoB@Example.com", "Passw0rd!"))
      yield assert(res.exists(_.userid == "u1"))
