package com.iscs.ratingbunny.routes

import cats.effect.{IO, Ref, Resource}
import cats.implicits.*
import com.iscs.ratingbunny.domains.PagedTitleData.titleDoc
import com.iscs.ratingbunny.domains.{AutoNameRec, AutoTitleRec, ImdbQueryImpl, Plan, SubscriptionStatus, TitleRec, UserDoc, UserRepo}
import com.iscs.ratingbunny.repos.HistoryRepo
import com.iscs.ratingbunny.security.JwtAuth
import io.circe.Json
import io.circe.generic.auto.*
import mongo4cats.bson.Document
import mongo4cats.circe.*
import mongo4cats.client.MongoClient
import mongo4cats.embedded.EmbeddedMongo
import munit.CatsEffectSuite
import org.http4s.*
import org.http4s.circe.*
import org.http4s.client.Client
import org.http4s.headers.Authorization
import org.http4s.implicits.*
import org.typelevel.ci.*
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}

import java.time.Instant
import scala.concurrent.duration.*

/** Drives the paged search routes over HTTP against a real ImdbQueryImpl on embedded Mongo, so the assertions cover the whole path from
  * request to `$skip`/`$limit` and the remaining-count header.
  */
class ImdbRoutesPagingSuite extends CatsEffectSuite with EmbeddedMongo:
  override def munitIOTimeout: Duration = 2.minutes

  override val mongoPort: Int = 12371
  private val secret          = "paging-secret"
  private val nconst          = "nm0000001"
  private val total           = 60

  private val pageSize = ImdbRoutes.PageSize

  private val titleIds = (1 to total).map(i => f"tt$i%07d").toList

  private val mongo = ResourceSuiteLocalFixture(
    "mongo",
    for
      _      <- EmbeddedMongo.start[IO](mongoPort, mongoUsername, mongoPassword, mongoVersion)
      client <- MongoClient.fromConnectionString[IO](s"mongodb://localhost:$mongoPort")
      db     <- Resource.eval(client.getDatabase("paging"))
      titles <- Resource.eval(db.getCollectionWithCodec[Document]("titles"))
      people <- Resource.eval(db.getCollectionWithCodec[Document]("people_titles"))
      _      <- Resource.eval(titles.insertMany((1 to total).map(i => titleDoc(i, f"tt$i%07d"))))
      _      <- Resource.eval(people.insertMany((1 to total).map(i => titleDoc(i, f"$nconst-tt$i%07d", Some(nconst)))))
    yield db
  )

  override def munitFixtures = List(mongo)

  private def imdbQuery(client: Option[Client[IO]]): IO[ImdbQueryImpl[IO]] =
    val db = mongo()
    for
      peopleFx       <- db.getCollectionWithCodec[AutoNameRec]("people")
      peopleTitlesFx <- db.getCollectionWithCodec[TitleRec]("people_titles")
      titlesFx       <- db.getCollectionWithCodec[TitleRec]("titles")
      autoTitlesFx   <- db.getCollectionWithCodec[AutoTitleRec]("titles")
    yield new ImdbQueryImpl[IO](peopleFx, peopleTitlesFx, titlesFx, autoTitlesFx, "localhost", client)

  private val user = UserDoc(
    email = "pager@example.com",
    email_norm = "pager@example.com",
    passwordHash = "hash",
    userid = "pager",
    plan = Plan.ProMonthly,
    status = SubscriptionStatus.Active,
    displayName = None,
    emailVerified = true
  )

  private val userRepo = new UserRepo[IO]:
    def findByEmail(email: String): IO[Option[UserDoc]]             = IO.pure(None)
    def insert(u: UserDoc): IO[Unit]                                = IO.unit
    def findByUserId(id: String): IO[Option[UserDoc]]               = IO.pure(Option.when(id == user.userid)(user))
    def findByVerificationToken(token: String): IO[Option[UserDoc]] = IO.pure(None)
    def markEmailVerified(uid: String): IO[Unit]                    = IO.unit
    def updateSubscription(uid: String, plan: Plan, status: SubscriptionStatus, trialEndsAt: Option[Instant]): IO[Unit] = IO.unit

  private val bearer =
    val claim = JwtClaim(subject = Some(user.userid), expiration = Some(Instant.now.plusSeconds(600).getEpochSecond))
    Authorization(Credentials.Token(AuthScheme.Bearer, JwtCirce.encode(claim, secret, JwtAlgorithm.HS256)))

  private val withYear    = Json.obj("year" -> Json.arr(Json.fromInt(2000), Json.fromInt(2100)))
  private val withoutYear = Json.obj()

  private def post(routes: HttpRoutes[IO], path: String, body: Json, auth: Boolean = false): IO[(List[String], Long)] =
    val req = Request[IO](Method.POST, Uri.unsafeFromString(path)).withEntity(body)
    routes
      .orNotFound(if auth then req.putHeaders(bearer) else req)
      .flatMap: resp =>
        assertEquals(resp.status, Status.Ok)
        assertEquals(resp.headers.get(ci"Access-Control-Expose-Headers").map(_.head.value), Some("X-Remaining-Count"))
        val remaining = resp.headers.get(ci"X-Remaining-Count").map(_.head.value.toLong).getOrElse(-1L)
        resp.as[Json].map(js => (js.asArray.toList.flatten.flatMap(_.hcursor.get[String]("_id").toOption), remaining))

  private def expectedPage(p: Int): List[String] = titleIds.slice((p - 1) * pageSize, p * pageSize)
  private def expectedRemaining(p: Int): Long    = math.max(total - p * pageSize, 0).toLong

  // Pages 1-4 of 60 records at 24 per page: two full pages, a page of 12, then an empty page past the end.
  private val expectedPages = (1 to 4).toList.map(p => (expectedPage(p), expectedRemaining(p)))

  private def publicRoutes(client: Option[Client[IO]] = None): IO[HttpRoutes[IO]] =
    for
      iq <- imdbQuery(client)
      hx <- HistoryRepo.make[IO](mongo())
    yield ImdbRoutes.publicRoutes[IO](iq, hx, secret)

  test("title pages are consecutive 24-record slices of the full result with an exact remaining count, and past the end is empty"):
    for
      routes <- publicRoutes()
      pages  <- (1 to 4).toList.traverse(p => post(routes, s"/title/$p/5.0", withYear))
    yield assertEquals(pages, expectedPages)

  test("window-size parameters from older clients are ignored, so they can neither change nor inflate the page"):
    for
      routes  <- publicRoutes()
      desktop <- post(routes, "/title/1/5.0?ws=1600&wh=900&cs=230&ch=345&off=0", withYear)
      huge    <- post(routes, "/title/1/5.0?ws=4000&wh=4000&cs=1&ch=1&off=0", withYear)
    yield assertEquals(List(desktop, huge), List.fill(2)((expectedPage(1), expectedRemaining(1))))

  test("a body without a year searches every year instead of returning nothing"):
    for
      routes           <- publicRoutes()
      (ids, remaining) <- post(routes, "/title/1/5.0", withoutYear)
    yield assertEquals((ids, remaining), (expectedPage(1), expectedRemaining(1)))

  // Answers every poster-path request and records which title ids were asked for.
  private def recordingImageClient(asked: Ref[IO, List[String]]): Client[IO] =
    Client[IO]: req =>
      val id = req.uri.path.segments.map(_.encoded).toList.drop(1).headOption.getOrElse("")
      Resource.eval(asked.update(id :: _)).as(Response[IO](Status.Ok).withEntity(Json.obj("path" -> Json.fromString(s"/$id.jpg"))))

  test("pathtitle fetches poster paths only for the records on the returned page"):
    for
      asked            <- Ref.of[IO, List[String]](Nil)
      routes           <- publicRoutes(Some(recordingImageClient(asked)))
      (ids, remaining) <- post(routes, "/pathtitle/2/5.0", withYear)
      requested        <- asked.get
    yield assertEquals((ids, remaining, requested.sorted), (expectedPage(2), expectedRemaining(2), expectedPage(2).sorted))

  test("pro name pages are consecutive 24-record slices with an exact remaining count, and past the end is empty"):
    for
      iq <- imdbQuery(None)
      hx <- HistoryRepo.make[IO](mongo())
      routes = ImdbRoutes.authedRoutes[IO](iq, hx, userRepo, JwtAuth.middleware[IO](secret))
      body   = withYear.deepMerge(Json.obj("query" -> Json.fromString(nconst)))
      pages <- (1 to 4).toList.traverse(p => post(routes, s"/name/$p/5.0", body, auth = true))
    yield assertEquals(pages, expectedPages)
