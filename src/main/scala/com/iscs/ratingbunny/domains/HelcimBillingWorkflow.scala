package com.iscs.ratingbunny.domains

import cats.data.EitherT
import cats.effect.{Async, Resource}
import cats.implicits.*
import com.iscs.helcim4s.HelcimClient
import com.iscs.helcim4s.core.{ClientBuilder, HelcimConfig}
import com.iscs.helcim4s.customer.models.{Address as HelcimAddress, CreateCustomer}
import com.iscs.helcim4s.recurring.models.CreateSubscription
import com.iscs.helcim4s.testkit.HelcimTestkit
import com.iscs.ratingbunny.config.TrialConfig
import com.iscs.ratingbunny.config.TrialWindow
import com.typesafe.scalalogging.Logger
import fs2.io.net.Network
import org.http4s.Uri

import java.time.{Instant, LocalDateTime, OffsetDateTime, ZoneOffset}
import scala.math.BigDecimal.RoundingMode
import scala.util.Try

object HelcimBillingWorkflow:
  final case class PlanIds(monthly: Long, yearly: Long)

  private val DefaultMonthly = 21361L
  private val DefaultAnnual  = 21362L

  private val L = Logger[HelcimBillingWorkflow.type]

  private def baseUri(mode: String): Uri =
    if mode == "live" then Uri.unsafeFromString("https://api.helcim.com/v2")
    else Uri.unsafeFromString("http://helcim-stub.local/v2")

  private def readPlanId(name: String, default: Long): Long =
    sys.env
      .get(name)
      .flatMap(_.toLongOption)
      .getOrElse(default)

  private def readToken(mode: String): String =
    sys.env.getOrElse("HELCIM_API_TOKEN", if mode == "stub" then "stub-token" else "")

  def make[F[_]: Async](trialConfig: TrialConfig)(using Network[F]): F[BillingWorkflow[F]] =
    val mode          = sys.env.getOrElse("HELCIM4S_MODE", "stub")
    val monthlyPlanId = readPlanId("PROMONTHLY", DefaultMonthly)
    val yearlyPlanId  = readPlanId("PROANNUAL", DefaultAnnual)
    val currency      = sys.env.getOrElse("HELCIM_DEFAULT_CURRENCY", "USD")
    val token =
      readToken(mode) match
        case "" if mode == "live" =>
          throw new IllegalStateException("HELCIM_API_TOKEN must be set when HELCIM4S_MODE=live")
        case other => other
    val cfg = HelcimConfig(
      baseUri = baseUri(mode),
      apiToken = token,
      maxConcurrent = 5,
      autoIdempotency = true
    )

    val clientResource: Resource[F, HelcimClient[F]] =
      if mode == "live" then
        ClientBuilder
          .resource[F](cfg)
          .flatMap(raw => HelcimTestkit.resourceFromClient(cfg, raw))
      else
        HelcimTestkit.resourceFromClient(cfg, HelcimTestkit.stubClient[F])

    Async[F].pure(
      new HelcimBillingWorkflow[F](clientResource, PlanIds(monthlyPlanId, yearlyPlanId), currency, mode, trialConfig)
    )

final class HelcimBillingWorkflow[F[_]: Async](
    clientResource: Resource[F, HelcimClient[F]],
    planIds: HelcimBillingWorkflow.PlanIds,
    defaultCurrency: String,
    mode: String,
    trialConfig: TrialConfig
) extends BillingWorkflow[F]:

  private val L = Logger[HelcimBillingWorkflow[F]]

  override def createBilling(
      user: UserDoc,
      details: SignupBilling,
      trialWindow: TrialWindow
  ): EitherT[F, SignupError, BillingInfo] =
    val targetPlan = user.plan match
      case Plan.ProMonthly => planIds.monthly
      case Plan.ProYearly  => planIds.yearly
      case Plan.Free =>
        return EitherT.leftT(SignupError.BillingFailed("free plan does not require Helcim provisioning"))

    val provisioning = clientResource.use: client =>
      for
        customer <- client.customer
          .create(
            CreateCustomer(
              billingAddress = HelcimAddress(
                name = details.fullName,
                street1 = details.address.line1,
                city = details.address.city,
                province = details.address.state,
                country = details.address.country,
                postalCode = details.address.postalCode,
                email = Some(user.email)
              )
            )
          )
          .map(_.asInstanceOf[AnyRef])
        subscription <-
          extractLong(customer, "customerId") match
            case Some(cid) =>
              client.recurring
                .createSubscription(
                  CreateSubscription(
                    planId = targetPlan,
                    customerId = cid
                  )
                )
                .map(_.asInstanceOf[AnyRef])
            case None =>
              Async[F].raiseError[AnyRef](RuntimeException("missing Helcim customer id"))
      yield buildBillingInfo(user, details, customer, subscription, targetPlan, trialWindow)

    EitherT(
      provisioning.attempt.map {
        case Right(info) => Right(info)
        case Left(err) =>
          val message = Option(err.getMessage).getOrElse(err.getClass.getSimpleName)
          L.error(s"[helcim4s] Failed to provision billing for ${user.email} in $mode mode: $message", err)
          Left(SignupError.BillingFailed(message))
      }
    )

  private def buildBillingInfo(
      user: UserDoc,
      details: SignupBilling,
      customer: AnyRef,
      subscription: AnyRef,
      planId: Long,
      trialWindow: TrialWindow
  ): BillingInfo =
    val customerKey =
      extractString(customer, "customerCode").orElse(extractLong(customer, "customerId").map(_.toString)).getOrElse(
        throw RuntimeException("Helcim customer identifier missing")
      )
    val subscriptionId =
      extractString(subscription, "subscriptionId").orElse(extractLong(subscription, "subscriptionId").map(_.toString)).getOrElse(
        throw RuntimeException("Helcim subscription identifier missing")
      )
    val status =
      if trialWindow.isActive then "trialing"
      else extractString(subscription, "status").getOrElse("pending")
    val nextBillAt =
      trialWindow.endsAt.orElse(
        extractInstant(subscription, "nextBillAt", "nextBillingAt", "nextBilling", "nextBillingDate", "nextBillDate")
      )
    val amountCents =
      extractBigDecimal(subscription, "amount", "planAmount", "recurringAmount").flatMap(toCents).getOrElse(0L)
    val currency = extractString(subscription, "currency").getOrElse(defaultCurrency)

    BillingInfo(
      userId = user.userid,
      gateway = BillingGateway.Helcim,
      helcim = Some(
        HelcimAccount(
          customerId = customerKey,
          defaultCardToken = Option(details.cardToken).filter(_.nonEmpty),
          defaultBankToken = None
        )
      ),
      address = details.address,
      subscription = Some(
        HelcimSubSnapshot(
          subscriptionId = subscriptionId,
          planId = planId.toString,
          status = status,
          nextBillAt = nextBillAt,
          amountCents = amountCents,
          currency = currency
        )
      ),
      trialEndsAt = trialWindow.endsAt
    )

  override def cancelSubscription(info: BillingInfo): EitherT[F, CancelTrialError, BillingInfo] =
    info.subscription match
      case None => EitherT.leftT[F, BillingInfo](CancelTrialError.MissingSubscription)
      case Some(snapshot) =>
        val program = clientResource.use: _ => Async[F].unit
        EitherT(
          program.attempt.map {
            case Right(_) =>
              val now     = Instant.now()
              val updated = snapshot.copy(status = "canceled", nextBillAt = None)
              Right(info.copy(subscription = Some(updated), trialEndsAt = None, updatedAt = now))
            case Left(err) =>
              val message = Option(err.getMessage).getOrElse(err.getClass.getSimpleName)
              Left(CancelTrialError.GatewayFailure(message))
          }
        )

  private def extractString(target: AnyRef, methodNames: String*): Option[String] =
    methodNames.toList.view
      .flatMap(name => invoke(target, name))
      .collectFirst { case s: String => s } orElse
      methodNames.toList.view
        .flatMap(name => invoke(target, name))
        .collectFirst { case other => other.toString }

  private def extractLong(target: AnyRef, methodNames: String*): Option[Long] =
    methodNames.toList.view
      .flatMap(name => invoke(target, name))
      .collectFirst {
        case l: Long                  => Some(l)
        case i: Int                   => Some(i.toLong)
        case s: String                => s.toLongOption
        case bd: BigDecimal           => Some(bd.toLong)
        case bd: java.math.BigDecimal => Some(BigDecimal(bd).toLong)
      }
      .flatten

  private def extractBigDecimal(target: AnyRef, methodNames: String*): Option[BigDecimal] =
    methodNames.toList.view
      .flatMap(name => invoke(target, name))
      .collectFirst {
        case bd: BigDecimal        => bd
        case bd: java.math.BigDecimal => BigDecimal(bd)
        case d: Double             => BigDecimal(d)
        case l: Long               => BigDecimal(l)
        case i: Int                => BigDecimal(i)
      }

  private def extractInstant(target: AnyRef, methodNames: String*): Option[Instant] =
    methodNames.toList.view
      .flatMap(name => invoke(target, name))
      .collectFirst(Function.unlift(toInstant))

  private def toInstant(value: Any): Option[Instant] =
    value match
      case inst: Instant         => Some(inst)
      case odt: OffsetDateTime   => Some(odt.toInstant)
      case ldt: LocalDateTime    => Some(ldt.toInstant(ZoneOffset.UTC))
      case s: String             => Try(Instant.parse(s)).toOption
      case _                     => None

  private def toCents(amount: BigDecimal): Option[Long] =
    Try((amount * 100).setScale(0, RoundingMode.HALF_UP).toLongExact).toOption

  private def invoke(target: AnyRef, method: String): Option[Any] =
    Try(target.getClass.getMethod(method)).toOption.flatMap: m =>
      Try(m.invoke(target)).toOption
