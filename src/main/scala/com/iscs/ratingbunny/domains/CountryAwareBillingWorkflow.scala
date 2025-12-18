package com.iscs.ratingbunny.domains

import cats.data.EitherT
import cats.effect.Async
import cats.implicits.*
import com.iscs.ratingbunny.config.{TrialConfig, TrialWindow}
import com.typesafe.scalalogging.Logger
import fs2.io.net.Network

object CountryAwareBillingWorkflow:
  private val HelcimCountries: Set[String] = Set("US", "CA")

  def make[F[_]: Async](trialConfig: TrialConfig)(using Network[F]): F[BillingWorkflow[F]] =
    for
      helcim <- HelcimBillingWorkflow.make[F](trialConfig)
      stripe <- StripeBillingWorkflow.make[F](trialConfig)
    yield CountryAwareBillingWorkflow[F](helcim, stripe, HelcimCountries)

  def apply[F[_]: Async](
      helcim: BillingWorkflow[F],
      stripe: BillingWorkflow[F],
      helcimCountries: Set[String]
  ): BillingWorkflow[F] =
    new CountryAwareBillingWorkflow[F](helcim, stripe, helcimCountries.map(_.trim.toUpperCase))

final class CountryAwareBillingWorkflow[F[_]: Async] private[
    domains
] (
    helcimWorkflow: BillingWorkflow[F],
    stripeWorkflow: BillingWorkflow[F],
    helcimCountries: Set[String]
) extends BillingWorkflow[F]:

  private val L = Logger[CountryAwareBillingWorkflow[F]]

  override def createBilling(
      user: UserDoc,
      details: SignupBilling,
      trialWindow: TrialWindow
  ): EitherT[F, SignupError, BillingInfo] =
    val gateway = selectGateway(details.address.country)
    L.debug(s"Selected $gateway gateway for ${user.email} (${details.address.country})")
    val provisioned = gateway match
      case BillingGateway.Helcim => helcimWorkflow.createBilling(user, details, trialWindow)
      case BillingGateway.Stripe => stripeWorkflow.createBilling(user, details, trialWindow)

    provisioned.subflatMap(ensureGateway(gateway, _))

  override def cancelSubscription(info: BillingInfo): EitherT[F, CancelTrialError, BillingInfo] =
    info.gateway match
      case BillingGateway.Helcim => helcimWorkflow.cancelSubscription(info)
      case BillingGateway.Stripe => stripeWorkflow.cancelSubscription(info)

  private def ensureGateway(
      expected: BillingGateway,
      info: BillingInfo
  ): Either[SignupError, BillingInfo] =
    if info.gateway == expected then Right(info)
    else
      val message = s"gateway mismatch: expected $expected but workflow returned ${info.gateway}"
      L.error(
        s"Billing workflow returned mismatched gateway for ${info.userId}: $message"
      )
      Left(SignupError.BillingFailed(message))

  private def selectGateway(country: String): BillingGateway =
    val normalized = Option(country).map(_.trim.toUpperCase).filter(_.nonEmpty)
    normalized match
      case Some(code) if helcimCountries.contains(code) => BillingGateway.Helcim
      case _                                            => BillingGateway.Stripe
