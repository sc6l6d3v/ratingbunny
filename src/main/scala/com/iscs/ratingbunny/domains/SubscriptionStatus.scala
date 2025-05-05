package com.iscs.ratingbunny.domains

import io.circe.{Codec as RealCodec, Decoder, Encoder}

enum SubscriptionStatus:
  case Active, Trialing, PastDue, Canceled

object SubscriptionStatus:
  private val byName =
    values.map(v => v.toString.toLowerCase -> v).toMap

  given Encoder[SubscriptionStatus] = Encoder.encodeString.contramap(_.toString.toLowerCase)
  given Decoder[SubscriptionStatus] = Decoder.decodeString.emap(s =>
    SubscriptionStatus.values
      .find(_.toString.equalsIgnoreCase(s))
      .toRight("Bad status")
  )

  /** Safe for Scala 3.5 rules: the codec is built *from* prior givens, it no longer competes in the implicit search for them.
    */
  given CodecPlan[SubscriptionStatus](using
      dec: Decoder[SubscriptionStatus],
      enc: Encoder[SubscriptionStatus]
  ): RealCodec[SubscriptionStatus] =
    RealCodec.from(dec, enc)
