package com.iscs.ratingbunny.routes

import cats.effect.{IO, Ref}
import com.iscs.ratingbunny.domains.{Plan, SubscriptionStatus, UserDoc, UserRepo}
import munit.CatsEffectSuite
import org.http4s.{Response, Status}

import java.time.Instant

class VerifiedUserRoutesSuite extends CatsEffectSuite:

  private def user(uid: String, verified: Boolean) = UserDoc(
    email = s"$uid@example.com",
    email_norm = s"$uid@example.com",
    passwordHash = "hash",
    userid = uid,
    plan = Plan.ProMonthly,
    status = SubscriptionStatus.Trialing,
    displayName = None,
    emailVerified = verified
  )

  private val users = Map("verified" -> user("verified", true), "unverified" -> user("unverified", false))

  private val userRepo = new UserRepo[IO]:
    def findByEmail(email: String): IO[Option[UserDoc]] = IO.pure(None)
    def insert(u: UserDoc): IO[Unit]                    = IO.unit
    def findByUserId(id: String): IO[Option[UserDoc]] =
      if id.startsWith("guest-") then IO.raiseError(new AssertionError("guests have no user record to look up"))
      else IO.pure(users.get(id))
    def findByVerificationToken(token: String): IO[Option[UserDoc]] = IO.pure(None)
    def markEmailVerified(uid: String): IO[Unit]                    = IO.unit
    def updateSubscription(uid: String, plan: Plan, status: SubscriptionStatus, trialEndsAt: Option[Instant]): IO[Unit] = IO.unit

  private object Gate extends VerifiedUserRoutes:
    def run(uid: String): IO[(Status, Boolean)] =
      for
        ran <- Ref[IO].of(false)
        res <- ensureVerified(uid, userRepo)(ran.set(true).as(Response[IO](Status.Ok)))
        did <- ran.get
      yield (res.status, did)

  test("a verified user gets through"):
    assertIO(Gate.run("verified"), (Status.Ok, true))

  test("an unverified user is refused before the route runs"):
    assertIO(Gate.run("unverified"), (Status.Forbidden, false))

  test("a user with no record is refused before the route runs"):
    assertIO(Gate.run("deleted"), (Status.Forbidden, false))

  test("a guest gets through without a user lookup"):
    assertIO(Gate.run("guest-0b5f"), (Status.Ok, true))
