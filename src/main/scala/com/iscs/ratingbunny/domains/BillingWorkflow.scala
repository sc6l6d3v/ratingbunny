package com.iscs.ratingbunny.domains

import cats.data.EitherT
import com.iscs.ratingbunny.config.TrialWindow

enum CancelTrialError:
  case MissingSubscription
  case GatewayFailure(override val message: String)

  def message: String = this match
    case MissingSubscription    => "subscription not found"
    case GatewayFailure(reason) => reason

trait BillingWorkflow[F[_]]:
  def createBilling(
      user: UserDoc,
      details: SignupBilling,
      trialWindow: TrialWindow
  ): EitherT[F, SignupError, BillingInfo]

  def cancelSubscription(info: BillingInfo): EitherT[F, CancelTrialError, BillingInfo]
