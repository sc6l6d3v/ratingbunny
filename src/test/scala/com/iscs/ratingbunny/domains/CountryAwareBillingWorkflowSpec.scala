package com.iscs.ratingbunny.domains

import cats.data.EitherT
import cats.effect.IO
import cats.effect.kernel.Ref
import cats.implicits.*
import munit.CatsEffectSuite

import java.time.Instant

class CountryAwareBillingWorkflowSpec extends CatsEffectSuite:

  private def trackingWorkflow(
      ref: Ref[IO, Int],
      gateway: BillingGateway
  ): BillingWorkflow[IO] =
    new BillingWorkflow[IO]:
      override def createBilling(user: UserDoc, details: SignupBilling): EitherT[IO, SignupError, BillingInfo] =
        EitherT.liftF(ref.update(_ + 1)).as(
          BillingInfo(
            userId = user.userid,
            gateway = gateway,
            helcim = gateway match
              case BillingGateway.Helcim => Some(HelcimAccount("stub-helcim"))
              case _                     => None,
            stripe = gateway match
              case BillingGateway.Stripe => Some(StripeAccount("stub-stripe"))
              case _                     => None,
            address = details.address
          )
        )

  private val baseUser = UserDoc(
    email = "test@example.com",
    email_norm = "test@example.com",
    passwordHash = "hash",
    userid = "test-user",
    plan = Plan.ProMonthly,
    status = SubscriptionStatus.Active,
    displayName = None,
    createdAt = Instant.now()
  )

  private val billingDetails = SignupBilling(
    fullName = "Example User",
    address = Address(
      line1 = "123 Road",
      city = "City",
      state = "ST",
      postalCode = "12345",
      country = "US"
    ),
    cardToken = "tok_test"
  )

  test("selects Helcim for US addresses"):
    for
      helcimCount <- Ref.of[IO, Int](0)
      stripeCount <- Ref.of[IO, Int](0)
      workflow = CountryAwareBillingWorkflow[IO](
        trackingWorkflow(helcimCount, BillingGateway.Helcim),
        trackingWorkflow(stripeCount, BillingGateway.Stripe),
        Set("US", "CA")
      )
      result <- workflow.createBilling(baseUser, billingDetails.copy(address = billingDetails.address.copy(country = "us"))).value
      counts <- (helcimCount.get, stripeCount.get).tupled
    yield
      assertEquals(result.isRight, true)
      assertEquals(counts, (1, 0))

  test("selects Stripe for non North American addresses"):
    val international = billingDetails.copy(address = billingDetails.address.copy(country = "fr"))
    for
      helcimCount <- Ref.of[IO, Int](0)
      stripeCount <- Ref.of[IO, Int](0)
      workflow = CountryAwareBillingWorkflow[IO](
        trackingWorkflow(helcimCount, BillingGateway.Helcim),
        trackingWorkflow(stripeCount, BillingGateway.Stripe),
        Set("US", "CA")
      )
      result <- workflow.createBilling(baseUser, international).value
      counts <- (helcimCount.get, stripeCount.get).tupled
    yield
      assertEquals(result.isRight, true)
      assertEquals(counts, (0, 1))
