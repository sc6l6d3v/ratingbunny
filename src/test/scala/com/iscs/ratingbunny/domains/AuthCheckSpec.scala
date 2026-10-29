package com.iscs.ratingbunny.domains

import cats.data.EitherT
import cats.effect.*
import cats.implicits.*
import com.iscs.ratingbunny.config.{TrialConfig, TrialWindow}
import com.iscs.ratingbunny.messaging.EmailJob
import com.iscs.ratingbunny.testkit.TestHasher
import com.iscs.ratingbunny.util.PasswordHasher
import com.mongodb.ErrorCategory.DUPLICATE_KEY
import com.mongodb.MongoWriteException
import mongo4cats.circe.*
import mongo4cats.client.MongoClient
import mongo4cats.collection.MongoCollection
import mongo4cats.database.MongoDatabase
import mongo4cats.embedded.EmbeddedMongo
import mongo4cats.models.collection.IndexOptions
import mongo4cats.operations.Index
import munit.CatsEffectSuite

import java.time.Instant
import scala.concurrent.Future
import scala.concurrent.duration.*

class AuthCheckSpec extends CatsEffectSuite with EmbeddedMongo with QuerySetup:

  override val mongoPort: Int           = 12355
  override def munitIOTimeout: Duration = 2.minutes
  private val hasher           = TestHasher.make[IO]
  private val stubPublishJob: EmailJob => IO[Unit] = _ => IO.unit
  private val trialConfig      = TrialConfig(enabled = false, lengthDays = 14, includeFreePlan = true)

  private val stubBillingWorkflow = new BillingWorkflow[IO]:
    override def createBilling(user: UserDoc, details: SignupBilling, trialWindow: TrialWindow) =
      EitherT.rightT(
        BillingInfo(
          userId = user.userid,
          gateway = BillingGateway.Helcim,
          helcim = Some(
            HelcimAccount(
              customerId = "cust-123",
              defaultCardToken = Some(details.cardToken)
            )
          ),
          address = details.address,
          subscription = Some(
            HelcimSubSnapshot(
              subscriptionId = "sub-1",
              planId = user.plan match
                case Plan.ProMonthly => "21361"
                case Plan.ProYearly  => "21362"
                case Plan.Free       => "0",
              status = "active",
              nextBillAt = None,
              amountCents = 1000L,
              currency = "USD"
            )
          ),
          trialEndsAt = trialWindow.endsAt
        )
      )

    override def cancelSubscription(info: BillingInfo) =
      EitherT.rightT(info)

  // ── helpers ──────────────────────────────────────────────────
  private def withMongo[A](f: MongoDatabase[IO] => IO[A]): Future[A] =
    withRunningEmbeddedMongo:
      MongoClient
        .fromConnectionString[IO](s"mongodb://localhost:$mongoPort")
        .use(_.getDatabase("test").flatMap(f))
    .unsafeToFuture()

  private def mkBilling(
      fullName: String = "Jane Doe",
      cardToken: String = "card-abc"
  ) =
    SignupBilling(
      fullName = fullName,
      address = Address(
        line1 = "123 Main St",
        city = "Metropolis",
        state = "NY",
        postalCode = "10001",
        country = "US"
      ),
      cardToken = cardToken
    )

  private def mkSignup(
      email: String = "alice@example.com",
      pwd: String = "Passw0rd!",
      plan: Plan = Plan.Free,
      billing: Option[SignupBilling] = None
  ) = SignupRequest(email, pwd, None, plan, billing)

  private def mkUser(userid: String, email: String) =
    UserDoc(
      email = email,
      email_norm = email.trim.toLowerCase,
      passwordHash = "x",
      userid = userid,
      plan = Plan.Free,
      status = SubscriptionStatus.Active,
      displayName = None,
      verificationTokenHash = Some(s"token-$email")
    )

  /** Runs `onHash` between the userid probe and the user insert, the window a concurrent signup races through. */
  private def racingHasher(onHash: IO[Unit]): PasswordHasher[IO] = new PasswordHasher[IO]:
    def hash(pw: String): IO[String]                    = onHash *> hasher.hash(pw)
    def verify(pw: String, stored: String): IO[Boolean] = hasher.verify(pw, stored)

  private def singleKeyIndexes(users: MongoCollection[IO, UserDoc], field: String) =
    users.listIndexes.map(_.filter(_.getDocument("key").exists(_.keys == Set(field))).toList)

  // ── tests ────────────────────────────────────────────────────
  test("signup succeeds for fresh user"):
    withMongo: db =>
      for
        users    <- db.getCollectionWithCodec[UserDoc]("users")
        prof     <- db.getCollectionWithCodec[UserProfileDoc]("user_profile")
        billingC <- db.getCollectionWithCodec[BillingInfo]("billing_info")
        svc = new AuthCheckImpl[IO](users, prof, billingC, hasher, stubPublishJob, stubBillingWorkflow, trialConfig)

        res <- svc.signup(mkSignup())
        _   <- IO(assert(res.exists(_.userid.nonEmpty), s"expected Right but got $res"))

        stored <- users.find(feq("email", "alice@example.com")).first
        countU <- users.count
        countP <- prof.count
        countB <- billingC.count
      yield
        assertEquals(countU, 1L)
        assertEquals(countP, 1L)
        assertEquals(countB, 0L)
        assertEquals(stored.exists(!_.emailVerified), true)
        assertEquals(stored.flatMap(_.verificationTokenHash).isDefined, true)
        assertEquals(stored.flatMap(_.verificationExpires).isDefined, true)

  test("signup fails on duplicate email"):
    withMongo: db =>
      for
        users    <- db.getCollectionWithCodec[UserDoc]("users")
        prof     <- db.getCollectionWithCodec[UserProfileDoc]("user_profile")
        billingC <- db.getCollectionWithCodec[BillingInfo]("billing_info")
        _ <- users.insertOne(
          UserDoc(
            email = "dup@example.com",
            email_norm = "dup@example.com".trim.toLowerCase,
            passwordHash = "x",
            userid = "dup",
            plan = Plan.Free,
            status = SubscriptionStatus.Active,
            displayName = None,
            createdAt = Instant.now()
          )
        )
        svc = new AuthCheckImpl[IO](users, prof, billingC, hasher, stubPublishJob, stubBillingWorkflow, trialConfig)
        res <- svc.signup(mkSignup(email = "dup@example.com"))
      yield assertEquals(res, Left(SignupError.EmailExists))

  test("signup fails on weak password"):
    withMongo: db =>
      for
        users    <- db.getCollectionWithCodec[UserDoc]("users")
        prof     <- db.getCollectionWithCodec[UserProfileDoc]("user_profile")
        billingC <- db.getCollectionWithCodec[BillingInfo]("billing_info")
        svc = new AuthCheckImpl[IO](users, prof, billingC, hasher, stubPublishJob, stubBillingWorkflow, trialConfig)
        res <- svc.signup(mkSignup(pwd = "short"))
      yield assertEquals(res, Left(SignupError.BadPassword))

  test("signup fails when email queue publish fails"):
    withMongo: db =>
      for
        users    <- db.getCollectionWithCodec[UserDoc]("users")
        prof     <- db.getCollectionWithCodec[UserProfileDoc]("user_profile")
        billingC <- db.getCollectionWithCodec[BillingInfo]("billing_info")
        failingPublisher = (job: EmailJob) => IO.raiseError(new RuntimeException("bad address"))
        svc = new AuthCheckImpl[IO](users, prof, billingC, hasher, failingPublisher, stubBillingWorkflow, trialConfig)
        res <- svc.signup(mkSignup())
      yield assertEquals(res, Left(SignupError.BadEmail))

  test("pro signup requires billing info"):
    withMongo: db =>
      for
        users    <- db.getCollectionWithCodec[UserDoc]("users")
        prof     <- db.getCollectionWithCodec[UserProfileDoc]("user_profile")
        billingC <- db.getCollectionWithCodec[BillingInfo]("billing_info")
        svc = new AuthCheckImpl[IO](users, prof, billingC, hasher, stubPublishJob, stubBillingWorkflow, trialConfig)
        res <- svc.signup(mkSignup(plan = Plan.ProMonthly))
        countU <- users.count
        countP <- prof.count
        countB <- billingC.count
      yield
        assertEquals(res, Left(SignupError.BillingRequired))
        assertEquals(countU, 0L)
        assertEquals(countP, 0L)
        assertEquals(countB, 0L)

  test("pro signup stores billing info"):
    withMongo: db =>
      for
        users    <- db.getCollectionWithCodec[UserDoc]("users")
        prof     <- db.getCollectionWithCodec[UserProfileDoc]("user_profile")
        billingC <- db.getCollectionWithCodec[BillingInfo]("billing_info")
        svc = new AuthCheckImpl[IO](users, prof, billingC, hasher, stubPublishJob, stubBillingWorkflow, trialConfig)
        res        <- svc.signup(mkSignup(plan = Plan.ProMonthly, billing = Some(mkBilling())))
        _          <- IO(assert(res.exists(_.userid.nonEmpty), s"expected success but got $res"))
        storedUser <- users.find(feq("email", "alice@example.com")).first
        billingDoc <- storedUser match
          case Some(u) => billingC.find(feq("userId", u.userid)).first
          case None    => IO.pure(None)
        countB <- billingC.count
      yield
        assertEquals(countB, 1L)
        assertEquals(billingDoc.flatMap(_.helcim.map(_.customerId)), Some("cust-123"))
        assertEquals(billingDoc.flatMap(_.address.line2), None)

  test("billing workflow failure bubbles up"):
    withMongo: db =>
      for
        users    <- db.getCollectionWithCodec[UserDoc]("users")
        prof     <- db.getCollectionWithCodec[UserProfileDoc]("user_profile")
        billingC <- db.getCollectionWithCodec[BillingInfo]("billing_info")
        failingWorkflow = new BillingWorkflow[IO]:
          override def createBilling(user: UserDoc, details: SignupBilling, trialWindow: TrialWindow) =
            EitherT.leftT(SignupError.BillingFailed("boom"))
          override def cancelSubscription(info: BillingInfo) = EitherT.rightT(info)
        svc = new AuthCheckImpl[IO](users, prof, billingC, hasher, stubPublishJob, failingWorkflow, trialConfig)
        res <- svc.signup(mkSignup(plan = Plan.ProMonthly, billing = Some(mkBilling())))
      yield assertEquals(res, Left(SignupError.BillingFailed("boom")))

  test("signup derives a sanitised userid from the email local part"):
    withMongo: db =>
      for
        users    <- db.getCollectionWithCodec[UserDoc]("users")
        prof     <- db.getCollectionWithCodec[UserProfileDoc]("user_profile")
        billingC <- db.getCollectionWithCodec[BillingInfo]("billing_info")
        svc = new AuthCheckImpl[IO](users, prof, billingC, hasher, stubPublishJob, stubBillingWorkflow, trialConfig)
        cases = List(
          "Alice.Smith+news@Example.com"   -> "alice.smithnews",
          "jo@example.com"                 -> "jouser",
          "+++@example.com"                -> "user",
          "guest-abc@example.com"          -> "user-guest-abc",
          s"${"b" * 40}@example.com"       -> "b" * 32
        )
        got      <- cases.traverse((email, _) => svc.signup(mkSignup(email = email)).map(_.map(_.userid)))
        profiles <- prof.find.all.map(_.map(_.userid).toSet)
      yield
        assertEquals(got, cases.map((_, uid) => Right(uid)))
        assertEquals(profiles, cases.map(_._2).toSet)

  test("concurrent signups with the same local part both succeed with distinct userids"):
    withMongo: db =>
      for
        users    <- db.getCollectionWithCodec[UserDoc]("users")
        prof     <- db.getCollectionWithCodec[UserProfileDoc]("user_profile")
        billingC <- db.getCollectionWithCodec[BillingInfo]("billing_info")
        _        <- UserRepoImpl.make[IO](users)
        slow = racingHasher(IO.sleep(300.millis))
        svc  = new AuthCheckImpl[IO](users, prof, billingC, slow, stubPublishJob, stubBillingWorkflow, trialConfig)
        res <- (svc.signup(mkSignup(email = "sam@a.com")), svc.signup(mkSignup(email = "sam@b.com"))).parTupled
        stored   <- users.find.all.map(_.map(_.userid).toList.sorted)
        profiles <- prof.find.all.map(_.map(_.userid).toList.sorted)
      yield
        val ids = List(res._1, res._2).map(_.map(_.userid))
        assertEquals(ids.collect { case Right(id) => id }.sorted, List("sam", "sam1"), s"got $ids")
        assertEquals(stored, List("sam", "sam1"))
        assertEquals(profiles, List("sam", "sam1"))

  test("a userid taken between probe and insert is retried with the next candidate"):
    withMongo: db =>
      for
        users    <- db.getCollectionWithCodec[UserDoc]("users")
        prof     <- db.getCollectionWithCodec[UserProfileDoc]("user_profile")
        billingC <- db.getCollectionWithCodec[BillingInfo]("billing_info")
        _        <- UserRepoImpl.make[IO](users)
        jobs     <- Ref.of[IO, List[EmailJob]](Nil)
        racer = racingHasher(users.insertOne(mkUser("bob", "other@example.com")).void)
        svc   = new AuthCheckImpl[IO](users, prof, billingC, racer, job => jobs.update(job :: _), stubBillingWorkflow, trialConfig)
        res      <- svc.signup(mkSignup(email = "bob@example.com"))
        mine     <- users.find(feq("email_norm", "bob@example.com")).first
        profiles <- prof.find.all.map(_.map(_.userid).toList)
        sent     <- jobs.get
      yield
        assertEquals(res, Right(SignupOK("bob1")))
        assertEquals(mine.map(_.userid), Some("bob1"))
        assertEquals(profiles, List("bob1"))
        assertEquals(sent.map(_.correlationId), List(Some("bob1")))

  test("userid retries stop after ten candidates"):
    withMongo: db =>
      for
        users    <- db.getCollectionWithCodec[UserDoc]("users")
        prof     <- db.getCollectionWithCodec[UserProfileDoc]("user_profile")
        billingC <- db.getCollectionWithCodec[BillingInfo]("billing_info")
        _        <- UserRepoImpl.make[IO](users)
        taken = ("bob" :: (1 to 9).map(i => s"bob$i").toList).map(uid => mkUser(uid, s"$uid@other.com"))
        racer = racingHasher(taken.traverse_(users.insertOne))
        svc   = new AuthCheckImpl[IO](users, prof, billingC, racer, stubPublishJob, stubBillingWorkflow, trialConfig)
        res    <- svc.signup(mkSignup(email = "bob@example.com"))
        countU <- users.count
        countP <- prof.count
      yield
        assertEquals(res, Left(SignupError.UserIdExists))
        assertEquals(countU, 10L)
        assertEquals(countP, 0L)

  test("an email collision at insert time is EmailExists without retrying"):
    withMongo: db =>
      for
        users    <- db.getCollectionWithCodec[UserDoc]("users")
        prof     <- db.getCollectionWithCodec[UserProfileDoc]("user_profile")
        billingC <- db.getCollectionWithCodec[BillingInfo]("billing_info")
        _        <- UserRepoImpl.make[IO](users)
        racer = racingHasher(users.insertOne(mkUser("someoneelse", "dana@example.com")).void)
        svc   = new AuthCheckImpl[IO](users, prof, billingC, racer, stubPublishJob, stubBillingWorkflow, trialConfig)
        res    <- svc.signup(mkSignup(email = "Dana@Example.com"))
        countU <- users.count
        countP <- prof.count
      yield
        assertEquals(res, Left(SignupError.EmailExists))
        assertEquals(countU, 1L)
        assertEquals(countP, 0L)

  test("a failed signup removes only the documents it inserted"):
    withMongo: db =>
      for
        users    <- db.getCollectionWithCodec[UserDoc]("users")
        prof     <- db.getCollectionWithCodec[UserProfileDoc]("user_profile")
        billingC <- db.getCollectionWithCodec[BillingInfo]("billing_info")
        _        <- prof.insertOne(UserProfileDoc("carol", avatarUrl = Some("https://orphan.example.com/a.png")))
        _ <- billingC.insertOne(
          BillingInfo(userId = "carol", helcim = Some(HelcimAccount("orphan-cust")), address = mkBilling().address)
        )
        failingPublisher = (_: EmailJob) => IO.raiseError(new RuntimeException("queue down"))
        svc = new AuthCheckImpl[IO](users, prof, billingC, hasher, failingPublisher, stubBillingWorkflow, trialConfig)
        res      <- svc.signup(mkSignup(email = "carol@example.com", plan = Plan.ProMonthly, billing = Some(mkBilling())))
        countU   <- users.count
        profiles <- prof.find.all.map(_.toList)
        billings <- billingC.find.all.map(_.toList)
      yield
        assertEquals(res, Left(SignupError.BadEmail))
        assertEquals(countU, 0L)
        assertEquals(profiles.map(_.avatarUrl), List(Some("https://orphan.example.com/a.png")))
        assertEquals(billings.flatMap(_.helcim.map(_.customerId)), List("orphan-cust"))

  test("an unexpected failure propagates instead of reporting a weak password"):
    withMongo: db =>
      for
        users    <- db.getCollectionWithCodec[UserDoc]("users")
        prof     <- db.getCollectionWithCodec[UserProfileDoc]("user_profile")
        billingC <- db.getCollectionWithCodec[BillingInfo]("billing_info")
        broken = racingHasher(IO.raiseError(new IllegalStateException("hasher unavailable")))
        svc    = new AuthCheckImpl[IO](users, prof, billingC, broken, stubPublishJob, stubBillingWorkflow, trialConfig)
        res <- svc.signup(mkSignup()).attempt
      yield assert(res.left.exists(_.getMessage == "hasher unavailable"), s"expected the error to propagate, got $res")

  test("ensureIndexes enforces unique userid and email_norm and accepts a hand-made equivalent"):
    withMongo: db =>
      for
        users <- db.getCollectionWithCodec[UserDoc]("users")
        _     <- users.createIndex(Index.ascending("email_norm"), IndexOptions(unique = true).name("email_norm_unique_idx"))
        _     <- UserRepoImpl.make[IO](users)
        _     <- UserRepoImpl.make[IO](users)
        emailIdx  <- singleKeyIndexes(users, "email_norm")
        useridIdx <- singleKeyIndexes(users, "userid")
        _         <- users.insertOne(mkUser("erin", "erin@a.com"))
        dup       <- users.insertOne(mkUser("erin", "erin@b.com")).attempt
      yield
        assertEquals(emailIdx.flatMap(_.getString("name")), List("email_norm_unique_idx"))
        assertEquals(useridIdx.flatMap(_.getBoolean("unique")), List(true))
        assert(
          dup.left.exists:
            case mw: MongoWriteException => mw.getError.getCategory == DUPLICATE_KEY
            case _                       => false
          ,
          s"expected a duplicate-key failure, got $dup"
        )

  test("ensureIndexes logs and continues when existing data violates uniqueness"):
    withMongo: db =>
      for
        users     <- db.getCollectionWithCodec[UserDoc]("users")
        _         <- users.insertMany(List(mkUser("dupe", "dupe@a.com"), mkUser("dupe", "dupe@b.com")))
        made      <- UserRepoImpl.make[IO](users).attempt
        useridIdx <- singleKeyIndexes(users, "userid")
        emailIdx  <- singleKeyIndexes(users, "email_norm")
      yield
        assert(made.isRight, s"startup must not fail on legacy duplicates, got $made")
        assertEquals(useridIdx, Nil)
        assertEquals(emailIdx.flatMap(_.getBoolean("unique")), List(true))
