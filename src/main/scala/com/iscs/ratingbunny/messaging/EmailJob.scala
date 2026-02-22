package com.iscs.ratingbunny.messaging

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

final case class EmailJob(
    kind: String,
    id: String,
    correlationId: Option[String] = None
)

object EmailJob:
  val KindContact      = "contact"
  val KindVerifySignup = "verify-signup"

  given Encoder[EmailJob] = deriveEncoder
