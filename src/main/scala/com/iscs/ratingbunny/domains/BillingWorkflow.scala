package com.iscs.ratingbunny.domains

import cats.data.EitherT

trait BillingWorkflow[F[_]]:
  def createBilling(user: UserDoc, details: SignupBilling): EitherT[F, SignupError, BillingInfo]
