package com.iscs.ratingbunny.domains

import cats.data.EitherT
import cats.effect.{Async, Resource}
import cats.implicits.*
import com.iscs.stripe4s.StripeClient
import com.iscs.stripe4s.StripeClientBuilder
import com.iscs.stripe4s.StripeConfig
import com.iscs.stripe4s.models.{
  Address as StripeAddress,
  CreateCustomer,
  CreateSubscription,
  Customer,
  PaymentBehavior,
  Subscription
}
import com.typesafe.scalalogging.Logger

object StripeBillingWorkflow:
  final case class PriceIds(monthly: String, yearly: String)

  private val DefaultMonthlyPrice = "price_pro_monthly_stub"
  private val DefaultYearlyPrice  = "price_pro_yearly_stub"

  private val L = Logger[StripeBillingWorkflow.type]

  private def readPriceId(name: String, default: String): String =
    sys.env.get(name).filter(_.nonEmpty).getOrElse(default)

  private def readMode(): String = sys.env.getOrElse("STRIPE_MODE", "stub")

  private def readConfig(): StripeConfig =
    val apiKey = sys.env.getOrElse(
      "STRIPE_API_KEY",
      throw new IllegalStateException("STRIPE_API_KEY must be set when STRIPE_MODE=live")
    )
    val stripeAccount = sys.env.get("STRIPE_ACCOUNT")
    val maxRetries    = sys.env.get("STRIPE_MAX_NETWORK_RETRIES").flatMap(_.toIntOption)
    StripeConfig(apiKey = apiKey, stripeAccount = stripeAccount, maxNetworkRetries = maxRetries)

  private object StubStripeClient:
    def resource[F[_]: Async]: Resource[F, StripeClient[F]] =
      Resource.pure(new StripeClient[F]:
        override val customers: StripeClient.Customers[F] = (request: CreateCustomer) =>
          Async[F].pure(
            Customer(
              id = s"stub_cus_${request.email.orElse(request.name).getOrElse("anonymous")}",
              email = request.email,
              name = request.name,
              phone = request.phone,
              defaultSource = request.paymentToken.map(token => s"src_${token.takeRight(6)}"),
              metadata = request.metadata
            )
          )

        override val subscriptions: StripeClient.Subscriptions[F] = (request: CreateSubscription) =>
          Async[F].pure(
            Subscription(
              id = s"stub_sub_${request.priceId}",
              status = "active",
              currentPeriodStart = None,
              currentPeriodEnd = None,
              cancelAt = None,
              collectionMethod = Some("charge_automatically"),
              currency = Some("usd"),
              amountCents = None,
              metadata = request.metadata
            )
          )
      )

  def make[F[_]: Async]: F[BillingWorkflow[F]] =
    val mode          = readMode()
    val monthlyPrice  = readPriceId("STRIPE_PRO_MONTHLY_PRICE", DefaultMonthlyPrice)
    val yearlyPrice   = readPriceId("STRIPE_PRO_YEARLY_PRICE", DefaultYearlyPrice)
    val priceIds      = PriceIds(monthlyPrice, yearlyPrice)
    val clientResource =
      if mode == "live" then StripeClientBuilder.resource[F](readConfig())
      else StubStripeClient.resource[F]
    Async[F].pure(new StripeBillingWorkflow[F](clientResource, priceIds, mode))

final class StripeBillingWorkflow[F[_]: Async](
    clientResource: Resource[F, StripeClient[F]],
    priceIds: StripeBillingWorkflow.PriceIds,
    mode: String
) extends BillingWorkflow[F]:

  private val L = Logger[StripeBillingWorkflow[F]]

  override def createBilling(user: UserDoc, details: SignupBilling): EitherT[F, SignupError, BillingInfo] =
    val targetPrice = user.plan match
      case Plan.ProMonthly => priceIds.monthly
      case Plan.ProYearly  => priceIds.yearly
      case Plan.Free =>
        return EitherT.leftT(SignupError.BillingFailed("free plan does not require Stripe provisioning"))

    val provisioning = clientResource.use: client =>
      for
        customer <- client.customers.create(
          CreateCustomer(
            email = Some(user.email),
            name = Some(details.fullName),
            address = Some(
              StripeAddress(
                line1 = Some(details.address.line1),
                line2 = details.address.line2,
                city = Some(details.address.city),
                state = Some(details.address.state),
                postalCode = Some(details.address.postalCode),
                country = Some(details.address.country)
              )
            ),
            paymentToken = Option(details.cardToken).filter(_.nonEmpty),
            metadata = Map(
              "userId" -> user.userid,
              "plan" -> user.plan.asString
            )
          )
        )
        subscription <- client.subscriptions.create(
          CreateSubscription(
            customerId = customer.id,
            priceId = targetPrice,
            paymentBehavior = PaymentBehavior.AllowIncomplete,
            metadata = Map(
              "userId" -> user.userid,
              "plan" -> user.plan.asString
            )
          )
        )
      yield buildBillingInfo(user, details, targetPrice, customer, subscription)

    EitherT(
      provisioning.attempt.map {
        case Right(info) => Right(info)
        case Left(err) =>
          val message = Option(err.getMessage).getOrElse(err.getClass.getSimpleName)
          L.error(s"[stripe4s] Failed to provision billing for ${user.email} in $mode mode: $message", err)
          Left(SignupError.BillingFailed(message))
      }
    )

  private def buildBillingInfo(
      user: UserDoc,
      details: SignupBilling,
      priceId: String,
      customer: Customer,
      subscription: Subscription
  ): BillingInfo =
    BillingInfo(
      userId = user.userid,
      gateway = BillingGateway.Stripe,
      helcim = None,
      stripe = Some(
        StripeAccount(
          customerId = customer.id,
          defaultSource = customer.defaultSource,
          metadata = customer.metadata
        )
      ),
      address = details.address,
      subscription = None,
      stripeSubscription = Some(
        StripeSubSnapshot(
          subscriptionId = subscription.id,
          priceId = priceId,
          status = subscription.status,
          currentPeriodStart = subscription.currentPeriodStart,
          currentPeriodEnd = subscription.currentPeriodEnd,
          cancelAt = subscription.cancelAt,
          collectionMethod = subscription.collectionMethod,
          amountCents = subscription.amountCents,
          currency = subscription.currency
        )
      )
    )
