package com.iscs.ratingbunny.domains

import io.circe.{Codec as RealCodec, Decoder, DecodingFailure, Encoder, HCursor}

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

  /** Safe for Scala 3.5 rules: the codec is built *from* prior givens, it no longer competes in the implicit search for them.
    */
  given CodecPlan[Plan](using
      dec: Decoder[Plan],
      enc: Encoder[Plan]
  ): RealCodec[Plan] =
    RealCodec.from(dec, enc)

  given Decoder[Plan] = Decoder.instance: (cur: HCursor) =>
    cur
      .as[String]
      .flatMap: raw =>
        fromString(raw) match
          case Some(p) => Right(p) // happy path
          case None =>
            println(s"[Plan-decoder] unexpected value '$raw'") // ← breakpoint here
            Left(DecodingFailure(s"Bad plan: $raw", cur.history))
