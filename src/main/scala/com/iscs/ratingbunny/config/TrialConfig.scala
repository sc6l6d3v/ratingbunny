package com.iscs.ratingbunny.config

import com.iscs.ratingbunny.domains.{Plan, SubscriptionStatus}

import java.time.Instant
import java.time.temporal.ChronoUnit

final case class TrialWindow(
    enabled: Boolean,
    lengthDays: Int,
    endsAt: Option[Instant]
):
  val normalizedLength: Int = if lengthDays < 0 then 0 else lengthDays
  def isActive: Boolean = enabled && normalizedLength > 0 && endsAt.nonEmpty
  def statusDuringTrial: SubscriptionStatus =
    if isActive then SubscriptionStatus.Trialing else SubscriptionStatus.Active
  def periodDays: Option[Int] = if isActive then Some(normalizedLength) else None

object TrialWindow:
  val Disabled: TrialWindow = TrialWindow(enabled = false, lengthDays = 0, endsAt = None)

final case class TrialConfig(
    enabled: Boolean,
    lengthDays: Int,
    includeFreePlan: Boolean
):
  private val normalizedLength = if lengthDays < 0 then 0 else lengthDays

  def windowFor(plan: Plan, now: Instant): TrialWindow =
    val eligible = includeFreePlan || plan != Plan.Free
    if !enabled || normalizedLength == 0 || !eligible then TrialWindow.Disabled
    else TrialWindow(enabled = true, lengthDays = normalizedLength, endsAt = Some(now.plus(normalizedLength.toLong, ChronoUnit.DAYS)))

object TrialConfig:
  private def parseBoolean(value: String): Boolean =
    value.trim.toLowerCase match
      case "1" | "true" | "yes" | "on"  => true
      case "0" | "false" | "no" | "off" => false
      case other                           => other.toBooleanOption.getOrElse(false)

  def fromEnv(env: Map[String, String] = sys.env): TrialConfig =
    val enabled        = env.get("TRIAL_ENABLED").exists(parseBoolean)
    val includeFree    = env.get("TRIAL_INCLUDE_FREE").forall(parseBoolean)
    val length         = env.get("TRIAL_LENGTH_DAYS").flatMap(_.toIntOption).filter(_ > 0).getOrElse(14)
    TrialConfig(enabled = enabled, lengthDays = length, includeFreePlan = includeFree)
