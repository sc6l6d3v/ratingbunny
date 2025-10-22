package com.iscs.ratingbunny.domains

import cats.data.{EitherT, NonEmptyList}
import cats.effect.*
import cats.implicits.*
import com.iscs.mail.EmailAttachment
import com.iscs.ratingbunny.testkit.{MockEmailService, TestHasher}
import emil.Mail
import jakarta.mail.SendFailedException
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
  private val hasher           = TestHasher.make[IO]
  private val stubEmailService = new MockEmailService[IO]()

  private val stubBillingWorkflow = new BillingWorkflow[IO]:
    override def createBilling(user: UserDoc, details: SignupBilling) =
      EitherT.rightT(
        BillingInfo(
          userId = user.userid,
          gateway = details.gateway,
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
          )
        )
      )

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

  // ── tests ────────────────────────────────────────────────────
  test("signup succeeds for fresh user"):
    withMongo: db =>
      for
        users    <- db.getCollectionWithCodec[UserDoc]("users")
        prof     <- db.getCollectionWithCodec[UserProfileDoc]("user_profile")
        billingC <- db.getCollectionWithCodec[BillingInfo]("billing_info")
        svc = new AuthCheckImpl[IO](users, prof, billingC, hasher, stubEmailService, stubBillingWorkflow)

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
        svc = new AuthCheckImpl[IO](users, prof, billingC, hasher, stubEmailService, stubBillingWorkflow)
        res <- svc.signup(mkSignup(email = "dup@example.com"))
      yield assertEquals(res, Left(SignupError.EmailExists))

  test("signup fails on weak password"):
    withMongo: db =>
      for
        users    <- db.getCollectionWithCodec[UserDoc]("users")
        prof     <- db.getCollectionWithCodec[UserProfileDoc]("user_profile")
        billingC <- db.getCollectionWithCodec[BillingInfo]("billing_info")
        svc = new AuthCheckImpl[IO](users, prof, billingC, hasher, stubEmailService, stubBillingWorkflow)
        res <- svc.signup(mkSignup(pwd = "short"))
      yield assertEquals(res, Left(SignupError.BadPassword))

  test("signup fails when email service rejects recipient"):
    withMongo: db =>
      for
        users    <- db.getCollectionWithCodec[UserDoc]("users")
        prof     <- db.getCollectionWithCodec[UserProfileDoc]("user_profile")
        billingC <- db.getCollectionWithCodec[BillingInfo]("billing_info")
        failingEmailService = new MockEmailService[IO]():
          override def sendEmail(
              to: String,
              subject: String,
              textBody: String,
              htmlBody: String,
              attachments: List[EmailAttachment[IO]]
          ): IO[NonEmptyList[String]] =
            IO.raiseError(new SendFailedException("bad address"))
          override def sendEmail(
              to: String,
              subject: String,
              textBody: String,
              htmlBody: String
          ): IO[NonEmptyList[String]] =
            sendEmail(to, subject, textBody, htmlBody, Nil)
          override def sendEmailToMultiple(
              recipients: List[String],
              subject: String,
              textBody: String,
              htmlBody: String,
              attachments: List[EmailAttachment[IO]]
          ): IO[List[NonEmptyList[String]]] =
            IO.raiseError(new SendFailedException("bad address"))
          override def sendEmailToMultiple(
              recipients: List[String],
              subject: String,
              textBody: String,
              htmlBody: String
          ): IO[List[NonEmptyList[String]]] =
            sendEmailToMultiple(recipients, subject, textBody, htmlBody, Nil)
          override def send(mail: Mail[IO]): IO[NonEmptyList[String]] =
            IO.raiseError(new SendFailedException("bad address"))
        svc = new AuthCheckImpl[IO](users, prof, billingC, hasher, failingEmailService, stubBillingWorkflow)
        res <- svc.signup(mkSignup())
      yield assertEquals(res, Left(SignupError.BadEmail))

  test("pro signup requires billing info"):
    withMongo: db =>
      for
        users    <- db.getCollectionWithCodec[UserDoc]("users")
        prof     <- db.getCollectionWithCodec[UserProfileDoc]("user_profile")
        billingC <- db.getCollectionWithCodec[BillingInfo]("billing_info")
        svc = new AuthCheckImpl[IO](users, prof, billingC, hasher, stubEmailService, stubBillingWorkflow)
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
        svc = new AuthCheckImpl[IO](users, prof, billingC, hasher, stubEmailService, stubBillingWorkflow)
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
          override def createBilling(user: UserDoc, details: SignupBilling) =
            EitherT.leftT(SignupError.BillingFailed("boom"))
        svc = new AuthCheckImpl[IO](users, prof, billingC, hasher, stubEmailService, failingWorkflow)
        res <- svc.signup(mkSignup(plan = Plan.ProMonthly, billing = Some(mkBilling())))
      yield assertEquals(res, Left(SignupError.BillingFailed("boom")))
