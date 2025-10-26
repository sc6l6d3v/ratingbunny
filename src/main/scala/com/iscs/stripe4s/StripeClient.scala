package com.iscs.stripe4s

import cats.effect.kernel.Resource
import cats.effect.Async
import cats.syntax.all.toFunctorOps
import com.iscs.stripe4s.models.{CreateCustomer, CreateSubscription, Customer, Subscription}

trait StripeClient[F[_]]:
  def customers: StripeClient.Customers[F]
  def subscriptions: StripeClient.Subscriptions[F]

object StripeClient:
  trait Customers[F[_]]:
    def create(request: CreateCustomer): F[Customer]

  trait Subscriptions[F[_]]:
    def create(request: CreateSubscription): F[Subscription]
    def cancel(subscriptionId: String): F[Subscription]

object StripeClientBuilder:
  def resource[F[_]: Async](config: StripeConfig): Resource[F, StripeClient[F]] =
    Resource.pure(new LiveStripeClient[F](config))

final private class LiveStripeClient[F[_]](config: StripeConfig)(using F: Async[F]) extends StripeClient[F]:

  // Stripe SDK global settings
  import com.stripe.Stripe
  Stripe.enableTelemetry = config.enableTelemetry

  import com.iscs.stripe4s.models.*
  import com.stripe.model.{Customer => StripeCustomer, Subscription => StripeSubscription}
  import com.stripe.net.RequestOptions
  import com.stripe.param.{CustomerCreateParams, SubscriptionCreateParams}
  import scala.jdk.CollectionConverters.*
  import java.time.Instant

  private val baseOptions: RequestOptions =
    val builder = RequestOptions
      .builder()
      .setApiKey(config.apiKey)
    config.stripeAccount.foreach(builder.setStripeAccount)
    config.maxNetworkRetries.foreach(retries => builder.setMaxNetworkRetries(Integer.valueOf(retries)))
    builder.build()

  private def requestOptions(idempotencyKey: Option[String]): RequestOptions =
    val builder = baseOptions.toBuilderFullCopy
    idempotencyKey.foreach(builder.setIdempotencyKey)
    builder.build()

  override val customers: StripeClient.Customers[F] = (request: CreateCustomer) =>
    val paramsBuilder = CustomerCreateParams.builder()
    request.email.foreach(paramsBuilder.setEmail)
    request.name.foreach(paramsBuilder.setName)
    request.phone.foreach(paramsBuilder.setPhone)
    request.description.foreach(paramsBuilder.setDescription)
    request.paymentToken.foreach(paramsBuilder.setSource)
    request.metadata.foreach((k, v) => paramsBuilder.putMetadata(k, v))
    request.address.foreach: addr =>
      val addressBuilder = CustomerCreateParams.Address.builder()
      addr.line1.foreach(addressBuilder.setLine1)
      addr.line2.foreach(addressBuilder.setLine2)
      addr.city.foreach(addressBuilder.setCity)
      addr.state.foreach(addressBuilder.setState)
      addr.postalCode.foreach(addressBuilder.setPostalCode)
      addr.country.foreach(addressBuilder.setCountry)
      paramsBuilder.setAddress(addressBuilder.build())

    val options = requestOptions(request.idempotencyKey)
    Async[F]
      .blocking(StripeCustomer.create(paramsBuilder.build(), options))
      .map(mapCustomer)

  override val subscriptions: StripeClient.Subscriptions[F] = new StripeClient.Subscriptions[F]:
    override def create(request: CreateSubscription): F[Subscription] =
      val paramsBuilder = SubscriptionCreateParams
        .builder()
        .setCustomer(request.customerId)

      val itemBuilder = SubscriptionCreateParams.Item.builder().setPrice(request.priceId)
      paramsBuilder.addItem(itemBuilder.build())

      request.trialPeriodDays.foreach(days => paramsBuilder.setTrialPeriodDays(Long.box(days.toLong)))

      // Apply coupon as a discount
      request.coupon.foreach: couponId =>
        val discount = SubscriptionCreateParams.Discount
          .builder()
          .setCoupon(couponId)
          .build()
        paramsBuilder.addDiscount(discount)

      request.defaultPaymentMethod.foreach(paramsBuilder.setDefaultPaymentMethod)
      request.metadata.foreach((k, v) => paramsBuilder.putMetadata(k, v))
      paramsBuilder.setPaymentBehavior(toStripePaymentBehavior(request.paymentBehavior))

      val options = requestOptions(request.idempotencyKey)
      Async[F]
        .blocking(StripeSubscription.create(paramsBuilder.build(), options))
        .map(mapSubscription)

    override def cancel(subscriptionId: String): F[Subscription] =
      val options = requestOptions(None)
      Async[F]
        .blocking(
          StripeSubscription
            .retrieve(subscriptionId, options)
            .cancel(Map.empty[String, AnyRef].asJava, options)
        )
        .map(mapSubscription)

  private def toStripePaymentBehavior(behavior: PaymentBehavior): SubscriptionCreateParams.PaymentBehavior =
    behavior match
      case PaymentBehavior.AllowIncomplete   => SubscriptionCreateParams.PaymentBehavior.ALLOW_INCOMPLETE
      case PaymentBehavior.DefaultIncomplete => SubscriptionCreateParams.PaymentBehavior.DEFAULT_INCOMPLETE
      case PaymentBehavior.ErrorIfIncomplete => SubscriptionCreateParams.PaymentBehavior.ERROR_IF_INCOMPLETE
      case PaymentBehavior.PendingIfIncomplete =>
        SubscriptionCreateParams.PaymentBehavior.PENDING_IF_INCOMPLETE

  private def mapCustomer(customer: StripeCustomer): Customer =
    Customer(
      id = customer.getId,
      email = Option(customer.getEmail),
      name = Option(customer.getName),
      phone = Option(customer.getPhone),
      defaultSource = Option(customer.getDefaultSource).map(identity),
      metadata = Option(customer.getMetadata).map(_.asScala.toMap).getOrElse(Map.empty)
    )

  private def mapSubscription(subscription: StripeSubscription): Subscription =
    val (itemStart, itemEnd) = computeCurrentPeriodFromItems(subscription)
    Subscription(
      id = subscription.getId,
      status = Option(subscription.getStatus).getOrElse("unknown"),
      currentPeriodStart = itemStart,
      currentPeriodEnd = itemEnd,
      cancelAt = toInstant(subscription.getCancelAt),
      collectionMethod = Option(subscription.getCollectionMethod).map(identity),
      currency = extractCurrency(subscription),
      amountCents = extractAmount(subscription),
      metadata = Option(subscription.getMetadata).map(_.asScala.toMap).getOrElse(Map.empty)
    )

  /** Stripe v30: subscription-level current_period_* removed. Aggregate item-level periods to a single window: start =
    * max(items.current_period_start) end = min(items.current_period_end) Matches Stripe's documented aggregation in filters.
    */
  private def computeCurrentPeriodFromItems(subscription: StripeSubscription): (Option[Instant], Option[Instant]) =
    val items =
      Option(subscription.getItems)
        .flatMap(coll => Option(coll.getData))
        .map(_.asScala.toList)
        .getOrElse(Nil)

    val starts = items.flatMap(i => Option(i.getCurrentPeriodStart).map(_.longValue))
    val ends   = items.flatMap(i => Option(i.getCurrentPeriodEnd).map(_.longValue))

    val startOpt =
      if starts.isEmpty then None else Some(Instant.ofEpochSecond(starts.max))
    val endOpt =
      if ends.isEmpty then None else Some(Instant.ofEpochSecond(ends.min))

    (startOpt, endOpt)

  private def extractAmount(subscription: StripeSubscription): Option[Long] =
    subscription.getItems.getData.asScala.toList
      .flatMap: item =>
        Option(item.getPrice)
          .flatMap(price => Option(price.getUnitAmount))
          .orElse(Option(item.getPlan).flatMap(plan => Option(plan.getAmount)))
          .map(_.longValue)
      .headOption

  private def extractCurrency(subscription: StripeSubscription): Option[String] =
    subscription.getItems.getData.asScala.toList
      .flatMap: item =>
        Option(item.getPrice)
          .flatMap(price => Option(price.getCurrency))
          .orElse(Option(item.getPlan).flatMap(plan => Option(plan.getCurrency)))
      .headOption

  private def toInstant(epochSeconds: java.lang.Long): Option[Instant] =
    Option(epochSeconds).map(sec => Instant.ofEpochSecond(sec.longValue))
