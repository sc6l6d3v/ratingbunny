package com.iscs.ratingbunny.domains

import io.circe.{Codec as RealCodec, Decoder, Encoder}
import io.circe.syntax.*

enum Plan derives CanEqual:
  case Free
  case ProMonthly
  case ProYearly

  /** Simple parser for incoming JSON strings */
  def asString: String = this match
    case Free       => "free"
    case ProMonthly => "pro_monthly"
    case ProYearly  => "pro_yearly"

object Plan:
  def fromString(s: String): Option[Plan] = s match
    case "free"        => Some(Plan.Free)
    case "pro_monthly" => Some(Plan.ProMonthly)
    case "pro_yearly"  => Some(Plan.ProYearly)
    case _             => None

  given Encoder[Plan] = Encoder.encodeString.contramap(_.asString)
  given Decoder[Plan] = Decoder.decodeString.emap(Plan.fromString(_).toRight("Bad plan"))

  /** Safe for Scala 3.5 rules: the codec is built *from* prior givens, it no longer competes in the implicit search for them.
    */
  given CodecPlan[Plan](using
      dec: Decoder[Plan],
      enc: Encoder[Plan]
  ): RealCodec[Plan] =
    RealCodec.from(dec, enc)
