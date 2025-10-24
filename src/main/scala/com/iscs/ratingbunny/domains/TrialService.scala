package com.iscs.ratingbunny.domains

import cats.data.EitherT
import cats.effect.Async
import cats.implicits.*
import mongo4cats.collection.MongoCollection

enum CancelTrialFailure:
  case UserNotFound
  case NotTrialing
  case TrialExpired
  case Billing(error: CancelTrialError)

trait TrialService[F[_]]:
  def cancelTrial(userId: String): F[Either[CancelTrialFailure, Unit]]

final class TrialServiceImpl[F[_]: Async](
    userRepo: UserRepo[F],
    billingCol: MongoCollection[F, BillingInfo],
    billingWorkflow: BillingWorkflow[F]
) extends TrialService[F] with QuerySetup:

  override def cancelTrial(userId: String): F[Either[CancelTrialFailure, Unit]] =
    val program = for
      user <- EitherT.fromOptionF(userRepo.findByUserId(userId), CancelTrialFailure.UserNotFound)
      now  <- EitherT.liftF(Async[F].realTimeInstant)
      _ <- EitherT.cond[F](user.status == SubscriptionStatus.Trialing, (), CancelTrialFailure.NotTrialing)
      _ <- EitherT.cond[F](user.trialEndsAt.forall(_.isAfter(now)), (), CancelTrialFailure.TrialExpired)
      billingOpt <- EitherT.liftF(billingCol.find(feq("userId", user.userid)).first)
      updatedBilling <- billingOpt match
        case Some(info) => billingWorkflow.cancelSubscription(info).leftMap(CancelTrialFailure.Billing.apply).map(Some(_))
        case None       => EitherT.rightT[F, CancelTrialFailure](Option.empty[BillingInfo])
      _ <- EitherT.liftF(userRepo.updateSubscription(user.userid, Plan.Free, SubscriptionStatus.Active, None))
      _ <- updatedBilling match
        case Some(doc) => EitherT.liftF(billingCol.replaceOne(feq("userId", user.userid), doc).void)
        case None      => EitherT.rightT[F, CancelTrialFailure](())
    yield ()

    program.value

object TrialServiceImpl:
  def make[F[_]: Async](
      userRepo: UserRepo[F],
      billingCol: MongoCollection[F, BillingInfo],
      billingWorkflow: BillingWorkflow[F]
  ): TrialService[F] =
    new TrialServiceImpl[F](userRepo, billingCol, billingWorkflow)
