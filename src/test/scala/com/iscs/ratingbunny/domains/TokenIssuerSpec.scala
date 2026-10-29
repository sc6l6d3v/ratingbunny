package com.iscs.ratingbunny.domains

import cats.effect.{IO, Resource}
import cats.implicits.*
import dev.profunktor.redis4cats.effect.Log.NoOp.*
import dev.profunktor.redis4cats.{Redis, RedisCommands}
import munit.CatsEffectSuite

import java.nio.file.{Files, Paths}
import scala.concurrent.duration.*

/** Runs TokenIssuerImpl against a throwaway redis-server with KEYS disabled, so any code path that still scans the keyspace fails
  * loudly. Skipped when no redis-server binary is on the PATH.
  */
class TokenIssuerSpec extends CatsEffectSuite:

  private val port = 16399

  private val redisBinary =
    sys.env.getOrElse("PATH", "").split(':').map(Paths.get(_, "redis-server")).find(Files.isExecutable)

  private def user(uid: String) = UserDoc(
    email = s"$uid@example.com",
    email_norm = s"$uid@example.com",
    passwordHash = "hash",
    userid = uid,
    plan = Plan.Free,
    status = SubscriptionStatus.Active,
    displayName = None,
    emailVerified = true
  )

  private val repo = new UserRepo[IO]:
    def findByEmail(email: String): IO[Option[UserDoc]]             = IO.pure(None)
    def insert(u: UserDoc): IO[Unit]                                = IO.unit
    def findByUserId(id: String): IO[Option[UserDoc]]               = IO.pure(Some(user(id)))
    def findByVerificationToken(token: String): IO[Option[UserDoc]] = IO.pure(None)
    def markEmailVerified(uid: String): IO[Unit]                    = IO.unit
    def updateSubscription(uid: String, plan: Plan, status: SubscriptionStatus, trialEndsAt: Option[java.time.Instant]): IO[Unit] =
      IO.unit

  private val serverRes: Resource[IO, Unit] =
    Resource
      .make(
        IO.blocking(
          new ProcessBuilder(
            redisBinary.get.toString,
            "--port",
            port.toString,
            "--save",
            "",
            "--appendonly",
            "no",
            "--rename-command",
            "KEYS",
            ""
          ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        )
      )(p => IO.blocking(p.destroy()) *> IO.blocking(p.waitFor()).void)
      .void

  private def clientRes: Resource[IO, RedisCommands[IO, String, String]] =
    Redis[IO].utf8(s"redis://localhost:$port")

  private val redisFixture = ResourceSuiteLocalFixture(
    "redis",
    serverRes.evalTap(_ => IO.sleep(300.millis)).flatMap(_ => clientRes)
  )

  override def munitFixtures = if redisBinary.isDefined then List(redisFixture) else Nil

  private def withIssuer(body: (RedisCommands[IO, String, String], TokenIssuer[IO]) => IO[Unit]): IO[Unit] =
    IO(assume(redisBinary.isDefined, "redis-server not on PATH")) *> {
      val redis = redisFixture()
      redis.flushAll *> body(redis, new TokenIssuerImpl[IO](redis, repo, "secret"))
    }

  test("revokeUser invalidates every refresh token of that user and no one else's"):
    withIssuer: (_, issuer) =>
      for
        a1 <- issuer.issue(user("alice"))
        a2 <- issuer.issue(user("alice"))
        b1 <- issuer.issue(user("bob"))
        _  <- issuer.revokeUser("alice")
        r1 <- issuer.rotate(a1.refresh)
        r2 <- issuer.rotate(a2.refresh)
        rb <- issuer.rotate(b1.refresh)
      yield
        assertEquals(r1, None)
        assertEquals(r2, None)
        assert(rb.isDefined, "bob's session must survive alice's revocation")

  test("rotate and revoke drop the consumed token from the user's index"):
    withIssuer: (redis, issuer) =>
      for
        t1 <- issuer.issue(user("carol"))
        t2 <- issuer.issue(user("carol"))
        r1 <- issuer.rotate(t1.refresh)
        _  <- issuer.revoke(t2.refresh)
        idx <- redis.zRange("refresh-user:carol", 0, -1)
      yield
        assert(r1.isDefined)
        assertEquals(idx.size, 1, s"only the rotated-in token should remain indexed, got $idx")

  test("guest sessions are indexed and revocable like any user"):
    withIssuer: (_, issuer) =>
      for
        g  <- issuer.issueGuest("guest-123")
        _  <- issuer.revokeUser("guest-123")
        rg <- issuer.rotate(g.refresh)
      yield assertEquals(rg, None)
