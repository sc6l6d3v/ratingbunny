package com.iscs.ratingbunny.routes

import cats.effect.IO
import com.iscs.ratingbunny.config.TrialConfig
import com.iscs.ratingbunny.domains.{Plan, SubscriptionStatus, UserDoc, UserRepo}
import com.iscs.ratingbunny.model.Requests.ReqParams
import com.iscs.ratingbunny.repos.HistoryRepo
import com.iscs.ratingbunny.security.JwtAuth
import io.circe.Json
import mongo4cats.bson.Document
import mongo4cats.bson.syntax.*
import mongo4cats.client.MongoClient
import mongo4cats.collection.MongoCollection
import mongo4cats.embedded.EmbeddedMongo
import munit.CatsEffectSuite
import org.http4s.*
import org.http4s.circe.*
import org.http4s.implicits.*
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}

import java.time.Instant
import scala.concurrent.duration.*

class HistoryRoutesSuite extends CatsEffectSuite with EmbeddedMongo:

  override val mongoPort: Int           = 12373
  override def munitIOTimeout: Duration = 2.minutes

  private val secret = "test-secret"
  private val uid    = "user1"

  private val verified = UserDoc(
    email = "user1@example.com",
    email_norm = "user1@example.com",
    passwordHash = "hash",
    userid = uid,
    plan = Plan.ProMonthly,
    status = SubscriptionStatus.Active,
    displayName = None,
    emailVerified = true
  )

  private val userRepo = new UserRepo[IO]:
    def findByEmail(email: String): IO[Option[UserDoc]]             = IO.pure(None)
    def insert(u: UserDoc): IO[Unit]                                = IO.unit
    def findByUserId(id: String): IO[Option[UserDoc]]               = IO.pure(Option.when(id == uid)(verified))
    def findByVerificationToken(token: String): IO[Option[UserDoc]] = IO.pure(None)
    def markEmailVerified(uid: String): IO[Unit]                    = IO.unit
    def updateSubscription(uid: String, plan: Plan, status: SubscriptionStatus, trialEndsAt: Option[Instant]): IO[Unit] = IO.unit

  private val trialOff = TrialConfig(enabled = false, lengthDays = 0, includeFreePlan = false)

  private val bearer = headers.Authorization(
    Credentials.Token(AuthScheme.Bearer, JwtCirce.encode(JwtClaim(subject = Some(uid)), secret, JwtAlgorithm.HS256))
  )

  private def withRoutes[A](test: (HttpApp[IO], HistoryRepo[IO], MongoCollection[IO, Document]) => IO[A]): IO[A] =
    withRunningEmbeddedMongo:
      MongoClient
        .fromConnectionString[IO](s"mongodb://localhost:$mongoPort")
        .use: client =>
          for
            db   <- client.getDatabase("test")
            repo <- HistoryRepo.make[IO](db)
            coll <- db.getCollection("user_history")
            app = HistoryRoutes.authedRoutes[IO](repo, userRepo, trialOff, JwtAuth.middleware[IO](secret)).orNotFound
            res <- test(app, repo, coll)
          yield res

  private def get(app: HttpApp[IO], uri: Uri): IO[Response[IO]] =
    app.run(Request[IO](Method.GET, uri).putHeaders(bearer))

  private def entry(sig: String, createdAt: Instant, query: String) =
    Document("userId" := uid, "sig" := sig, "createdAt" := createdAt, "params" := Document("query" := query))

  test("GET /history/latest returns exactly sig, ISO createdAt, params without nulls, and hits"):
    val params = ReqParams(query = Some("Tenet"), year = Some(List(2020, 2021)))
    withRoutes: (app, repo, _) =>
      for
        _    <- repo.log(uid, params)
        resp <- get(app, uri"/history/latest")
        body <- resp.as[Json]
      yield
        assertEquals(resp.status, Status.Ok)
        val items = body.asArray.get
        assertEquals(items.size, 1)
        val item = items.head.asObject.get
        assertEquals(item.keys.toSet, Set("sig", "createdAt", "params", "hits"))
        assertEquals(item("sig"), Some(Json.fromString(repo.sigFor(params))))
        val createdAt = item("createdAt").flatMap(_.asString)
        assert(createdAt.exists(s => scala.util.Try(Instant.parse(s)).isSuccess), s"createdAt not ISO-8601: ${item("createdAt")}")
        assertEquals(
          item("params"),
          Some(Json.obj("query" -> Json.fromString("Tenet"), "year" -> Json.arr(Json.fromInt(2020), Json.fromInt(2021))))
        )
        assertEquals(item("hits"), Some(Json.fromInt(1)))

  test("GET /history/popular orders by hits then recency, reads legacy numeric hits, skips unreadable entries"):
    val t0 = Instant.parse("2026-01-01T00:00:00Z")
    withRoutes: (app, _, coll) =>
      for
        _ <- coll.insertMany(
          List(
            entry("a" * 64, t0, "alpha").add("hits" := 2),
            entry("b" * 64, t0.plusSeconds(60), "bravo").add("hits" := 2.0),
            entry("c" * 64, t0.plusSeconds(120), "charlie").add("hits" := 1L),
            Document("userId" := uid, "sig" := "d" * 64, "createdAt" := t0.plusSeconds(180), "hits" := 9)
          )
        )
        popular <- get(app, uri"/history/popular").flatMap(_.as[Json])
        latest  <- get(app, uri"/history/latest").flatMap(_.as[Json])
      yield
        def sigs(j: Json)  = j.asArray.get.toList.flatMap(_.hcursor.get[String]("sig").toOption)
        def hits(j: Json)  = j.asArray.get.toList.flatMap(_.hcursor.get[Long]("hits").toOption)
        assertEquals(sigs(popular), List("b" * 64, "a" * 64, "c" * 64))
        assertEquals(hits(popular), List(2L, 2L, 1L))
        assertEquals(sigs(latest), List("c" * 64, "b" * 64, "a" * 64))

  test("POST /history/byparams finds the entry for an equivalent spelling"):
    val logged = ReqParams(query = Some("Tenet"), genre = Some(List("Drama", "Comedy")), sortType = Some("rating"))
    val asked  = Json.obj(
      "query"    -> Json.fromString("  TENET"),
      "genre"    -> Json.arr(Json.fromString("Comedy"), Json.fromString("Drama")),
      "sortType" -> Json.fromString("year")
    )
    withRoutes: (app, repo, _) =>
      for
        _     <- repo.log(uid, logged)
        found <- app.run(Request[IO](Method.POST, uri"/history/byparams").putHeaders(bearer).withEntity(asked))
        body  <- found.as[Json]
        missing <- app.run(
          Request[IO](Method.POST, uri"/history/byparams").putHeaders(bearer).withEntity(Json.obj("query" -> Json.fromString("dune")))
        )
      yield
        assertEquals(found.status, Status.Ok)
        assertEquals(body, Json.obj("sig" -> Json.fromString(repo.sigFor(logged))))
        assertEquals(missing.status, Status.NotFound)

  test("GET /history/bysig no longer exposes who else ran a search"):
    val params = ReqParams(query = Some("tenet"))
    withRoutes: (app, repo, _) =>
      for
        _    <- repo.log("someone-else", params)
        resp <- get(app, Uri.unsafeFromString(s"/history/bysig/${repo.sigFor(params)}"))
      yield assertEquals(resp.status, Status.NotFound)
