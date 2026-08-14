package com.iscs.ratingbunny.routes

import cats.effect.IO
import munit.CatsEffectSuite
import org.http4s._
import org.http4s.implicits._
import org.http4s.circe._
import io.circe.Json
import io.circe.generic.auto._
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}
import com.iscs.ratingbunny.domains.{
  AuthCheck,
  AuthLogin,
  LoginError,
  LoginRequest,
  Plan,
  SignupError,
  SignupRequest,
  SubscriptionStatus,
  PasswordResetConfirmRequest,
  PasswordResetRequest,
  PasswordResetService,
  TokenIssuer,
  TokenPair,
  TrialService,
  UserDoc,
  UserRepo
}
import com.iscs.ratingbunny.security.JwtAuth

class AuthRoutesSuite extends CatsEffectSuite:

  private val user = UserDoc(
    email = "test@example.com",
    email_norm = "test@example.com".trim.toLowerCase,
    passwordHash = "hash",
    userid = "user1",
    plan = Plan.Free,
    status = SubscriptionStatus.Active,
    displayName = Some("Tester"),
    emailVerified = true
  )

  private val unverified = user.copy(emailVerified = false, verificationExpires = Some(java.time.Instant.now.plusSeconds(3600)))

  private val repo = new UserRepo[IO]:
    def findByEmail(email: String): IO[None.type]                   = IO.pure(None)
    def insert(u: UserDoc): IO[Unit]                                = IO.unit
    def findByUserId(id: String): IO[Option[UserDoc]]               = IO.pure(if id == user.userid then Some(user) else None)
    def findByVerificationToken(token: String): IO[Option[UserDoc]] = IO.pure(Some(unverified))
    def markEmailVerified(uid: String): IO[Unit]                    = IO.unit
    def updateSubscription(
        uid: String,
        plan: Plan,
        status: SubscriptionStatus,
        trialEndsAt: Option[java.time.Instant]
    ): IO[Unit] = IO.unit

  private val secret    = "test-secret"
  private val authMw    = JwtAuth.middleware[IO](secret)

  private val stubTrialService = new TrialService[IO]:
    def cancelTrial(userId: String) = IO.pure(Right(()))

  private val authedSvc = AuthRoutes.authedRoutes[IO](repo, stubTrialService, authMw)

  private val stubCheck = new AuthCheck[IO]:
    def signup(req: SignupRequest) = IO.pure(Left(SignupError.BadEmail))

  private val stubLogin = new AuthLogin[IO]:
    def login(req: LoginRequest) = IO.pure(Left(LoginError.UserNotFound))

  private val stubToken = new TokenIssuer[IO]:
    def issue(u: UserDoc)       = IO.pure(TokenPair("access", "refresh"))
    def issueGuest(uid: String) = IO.pure(TokenPair(s"$uid-a", s"$uid-r"))
    def rotate(r: String)       = IO.pure(None)
    def revoke(r: String)       = IO.unit
    def revokeUser(uid: String) = IO.unit

  private val stubPasswordReset = new PasswordResetService[IO]:
    def requestReset(req: PasswordResetRequest, requestIp: Option[String]) = IO.unit
    def confirmReset(req: PasswordResetConfirmRequest) = IO.pure(Right(()))

  private val httpSvc = AuthRoutes.httpRoutes[IO](stubCheck, stubLogin, repo, stubToken, stubPasswordReset).orNotFound

  private val token = JwtCirce.encode(JwtClaim(subject = Some(user.userid)), secret, JwtAlgorithm.HS256)

  /** Login service stubbed to always fail with the given error, wired into the public routes. */
  private def svcFailingLoginWith(err: LoginError) =
    val failing = new AuthLogin[IO]:
      def login(req: LoginRequest) = IO.pure(Left(err))
    AuthRoutes.httpRoutes[IO](stubCheck, failing, repo, stubToken, stubPasswordReset).orNotFound

  private val loginBody = Json.obj("email" -> Json.fromString(user.email), "password" -> Json.fromString("secret"))

  /** A 401 must carry the Bearer challenge and a JSON error body. */
  private def assertUnauthorized(resp: Response[IO], expectedMessage: String) =
    assertEquals(resp.status, Status.Unauthorized)
    assertEquals(
      resp.headers.get[headers.`WWW-Authenticate`].map(_.values.head.scheme),
      Some("Bearer")
    )
    resp
      .as[Json]
      .map: json =>
        assertEquals(json.hcursor.get[String]("error"), Right(expectedMessage))

  private def assertLogin401(err: LoginError, expectedMessage: String) =
    val req = Request[IO](Method.POST, uri"/auth/login").withEntity(loginBody)
    svcFailingLoginWith(err).run(req).flatMap(assertUnauthorized(_, expectedMessage))

  test("POST /auth/login returns 401 when the user is not found"):
    assertLogin401(LoginError.UserNotFound, "user not found")

  test("POST /auth/login returns 401 on a wrong password"):
    assertLogin401(LoginError.BadPassword, "wrong password")

  test("POST /auth/refresh returns 401 without a bearer token"):
    httpSvc
      .run(Request[IO](Method.POST, uri"/auth/refresh"))
      .flatMap(assertUnauthorized(_, "missing bearer token"))

  test("POST /auth/refresh returns 401 when the token cannot be rotated"):
    // stubToken.rotate always yields None
    val req = Request[IO](Method.POST, uri"/auth/refresh")
      .putHeaders(headers.Authorization(Credentials.Token(AuthScheme.Bearer, "stale-refresh")))
    httpSvc.run(req).flatMap(assertUnauthorized(_, "invalid or expired token"))

  test("POST /auth/logout returns 401 without a bearer token"):
    httpSvc
      .run(Request[IO](Method.POST, uri"/auth/logout"))
      .flatMap(assertUnauthorized(_, "missing bearer token"))

  test("GET /auth/me returns user info"):
    val req = Request[IO](Method.GET, uri"/auth/me")
      .putHeaders(headers.Authorization(Credentials.Token(AuthScheme.Bearer, token)))

    authedSvc.orNotFound
      .run(req)
      .flatMap: resp =>
        assertEquals(resp.status, Status.Ok)
        resp
          .as[Json]
          .map: json =>
            assertEquals(json.hcursor.get[String]("userid"), Right(user.userid))
            assertEquals(json.hcursor.get[String]("email"), Right(user.email))

  test("POST /auth/guest issues tokens"):
    val req = Request[IO](Method.POST, uri"/auth/guest")
    httpSvc
      .run(req)
      .flatMap: resp =>
        assertEquals(resp.status, Status.Ok)
        resp
          .as[Json]
          .map: json =>
            val a = json.hcursor.get[String]("access").toOption.get
            val r = json.hcursor.get[String]("refresh").toOption.get
            assert(a.contains("guest-"))
            assert(r.contains("guest-"))

  test("GET /auth/verify returns tokens"):
    val req = Request[IO](Method.GET, uri"/auth/verify?token=abc")
    httpSvc
      .run(req)
      .flatMap: resp =>
        assertEquals(resp.status, Status.Ok)
        resp
          .as[Json]
          .map: json =>
            assert(json.hcursor.get[String]("access").isRight)
            assert(json.hcursor.get[String]("refresh").isRight)

  test("POST /auth/trial/cancel returns 204"):
    val token = JwtCirce.encode(JwtClaim(subject = Some(user.userid)), secret, JwtAlgorithm.HS256)
    val req = Request[IO](Method.POST, uri"/auth/trial/cancel")
      .putHeaders(headers.Authorization(Credentials.Token(AuthScheme.Bearer, token)))
    authedSvc.orNotFound
      .run(req)
      .map(resp => assertEquals(resp.status, Status.NoContent))
