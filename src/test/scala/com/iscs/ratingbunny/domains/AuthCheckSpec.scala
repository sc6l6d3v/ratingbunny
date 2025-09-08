package com.iscs.ratingbunny.domains

import cats.data.NonEmptyList
import cats.effect.*
import cats.implicits.*
import com.iscs.mail.EmailService
import com.iscs.ratingbunny.testkit.{MockEmailService, TestHasher}
import io.circe.generic.auto.*
import mongo4cats.circe.*
import mongo4cats.client.MongoClient
import mongo4cats.database.MongoDatabase
import mongo4cats.embedded.EmbeddedMongo
import munit.CatsEffectSuite

import java.time.Instant
import scala.concurrent.Future
import scala.concurrent.duration.*

class AuthCheckSpec extends CatsEffectSuite with EmbeddedMongo with QuerySetup:

  override val mongoPort: Int           = 12355
  override def munitIOTimeout: Duration = 2.minutes
  private val hasher                    = TestHasher.make[IO]
  private val stubEmailService          = new MockEmailService[IO]()

  // ── helpers ──────────────────────────────────────────────────
  private def withMongo[A](f: MongoDatabase[IO] => IO[A]): Future[A] =
    withRunningEmbeddedMongo:
      MongoClient
        .fromConnectionString[IO](s"mongodb://localhost:$mongoPort")
        .use(_.getDatabase("test").flatMap(f))
    .unsafeToFuture()

  private def mkSignup(
      email: String = "alice@example.com",
      pwd: String = "Passw0rd!",
      plan: Plan = Plan.Free
  ) = SignupRequest(email, pwd, None, plan)

  // ── tests ────────────────────────────────────────────────────
  test("signup succeeds for fresh user"):
    withMongo: db =>
      for
        users <- db.getCollectionWithCodec[UserDoc]("users")
        prof  <- db.getCollectionWithCodec[UserProfileDoc]("user_profile")
        svc = new AuthCheckImpl[IO](users, prof, hasher, stubEmailService)

        res <- svc.signup(mkSignup())
        _   <- IO(assert(res.exists(_.userid.nonEmpty), s"expected Right but got $res"))

        stored <- users.find(feq("email", "alice@example.com")).first
        countU <- users.count
        countP <- prof.count
      yield
        assertEquals(countU, 1L)
        assertEquals(countP, 1L)
        assertEquals(stored.exists(!_.emailVerified), true)
        assertEquals(stored.flatMap(_.verificationTokenHash).isDefined, true)
        assertEquals(stored.flatMap(_.verificationExpires).isDefined, true)

  test("signup fails on duplicate email"):
    withMongo: db =>
      for
        users <- db.getCollectionWithCodec[UserDoc]("users")
        prof  <- db.getCollectionWithCodec[UserProfileDoc]("user_profile")
        _ <- users.insertOne(
          UserDoc(
            email = "dup@example.com",
            passwordHash = "x",
            userid = "dup",
            plan = Plan.Free,
            status = SubscriptionStatus.Active,
            displayName = None,
            createdAt = Instant.now()
          )
        )
        svc = new AuthCheckImpl[IO](users, prof, hasher, stubEmailService)
        res <- svc.signup(mkSignup(email = "dup@example.com"))
      yield assertEquals(res, Left(SignupError.EmailExists))

  test("signup fails on weak password"):
    withMongo: db =>
      for
        users <- db.getCollectionWithCodec[UserDoc]("users")
        prof  <- db.getCollectionWithCodec[UserProfileDoc]("user_profile")
        svc = new AuthCheckImpl[IO](users, prof, hasher, stubEmailService)
        res <- svc.signup(mkSignup(pwd = "short"))
      yield assertEquals(res, Left(SignupError.BadPassword))
