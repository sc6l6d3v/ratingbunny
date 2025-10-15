package com.iscs.stripe4s

final case class StripeConfig(
    apiKey: String,
    stripeAccount: Option[String] = None,
    maxNetworkRetries: Option[Int] = None,
    enableTelemetry: Boolean = true
)
