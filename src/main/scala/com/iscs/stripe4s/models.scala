package com.iscs.stripe4s

import java.time.Instant

object models:

  final case class Address(
      line1: Option[String] = None,
      line2: Option[String] = None,
      city: Option[String] = None,
      state: Option[String] = None,
      postalCode: Option[String] = None,
      country: Option[String] = None
  )

  final case class CreateCustomer(
      email: Option[String] = None,
      name: Option[String] = None,
      address: Option[Address] = None,
      phone: Option[String] = None,
      paymentToken: Option[String] = None,
      description: Option[String] = None,
      metadata: Map[String, String] = Map.empty,
      idempotencyKey: Option[String] = None
  )

  final case class Customer(
      id: String,
      email: Option[String],
      name: Option[String],
      phone: Option[String],
      defaultSource: Option[String],
      metadata: Map[String, String]
  )

  enum PaymentBehavior:
    case AllowIncomplete, DefaultIncomplete, ErrorIfIncomplete, PendingIfIncomplete

  final case class CreateSubscription(
      customerId: String,
      priceId: String,
      trialPeriodDays: Option[Int] = None,
      coupon: Option[String] = None,
      paymentBehavior: PaymentBehavior = PaymentBehavior.AllowIncomplete,
      defaultPaymentMethod: Option[String] = None,
      metadata: Map[String, String] = Map.empty,
      idempotencyKey: Option[String] = None
  )

  final case class Subscription(
      id: String,
      status: String,
      currentPeriodStart: Option[Instant],
      currentPeriodEnd: Option[Instant],
      cancelAt: Option[Instant],
      collectionMethod: Option[String],
      currency: Option[String],
      amountCents: Option[Long],
      metadata: Map[String, String]
  )
