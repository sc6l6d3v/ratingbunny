package com.iscs.ratingbunny

import com.iscs.ratingbunny.model.Requests.ReqParams
import mongo4cats.bson.ObjectId

import java.time.Instant
import scala.language.postfixOps

import io.circe.{Codec, Decoder, Encoder}
import io.circe.generic.semiauto.*

package object domains:

  final private case class PathRec(path: String)

  trait AutoRecBase

  final case class AutoNameRec(firstName: String, lastName: Option[String]) extends AutoRecBase

  final case class AutoTitleRec(primaryTitle: Option[String]) extends AutoRecBase

  trait TitleRecBase:
    val _id: String
    val averageRating: Option[Double]
    val numVotes: Option[Int]
    val titleType: String
    val primaryTitle: String
    val originalTitle: String
    val isAdult: Int
    val startYear: Int
    val endYear: Int
    val runtimeMinutes: Option[Int]
    val genresList: Option[List[String]]

  final case class TitleRec(
      _id: String,
      averageRating: Option[Double],
      numVotes: Option[Int],
      titleType: String,
      primaryTitle: String,
      originalTitle: String,
      isAdult: Int,
      startYear: Int,
      endYear: Int,
      runtimeMinutes: Option[Int],
      genresList: Option[List[String]]
  ) extends TitleRecBase

  final case class TitleRecPath(
      _id: String,
      averageRating: Option[Double],
      numVotes: Option[Int],
      titleType: String,
      primaryTitle: String,
      originalTitle: String,
      isAdult: Int,
      startYear: Int,
      endYear: Int,
      runtimeMinutes: Option[Int],
      genresList: Option[List[String]],
      posterPath: Option[String]
  ) extends TitleRecBase

  final case class UserHistory(
      _id: String,
      userId: String,
      createdAt: Instant,
      params: ReqParams,
      sig: String,
      hits: Int
  )

  /** -- incoming payload */
  final case class SignupBilling(
      fullName: String,
      address: Address,
      cardToken: String,
      gateway: BillingGateway = BillingGateway.Helcim
  )

  object SignupBilling:
    given Codec[SignupBilling] = deriveCodec

  final case class SignupRequest(
      email: String,
      password: String,
      displayName: Option[String],
      plan: Plan,
      billing: Option[SignupBilling] = None
  )

  /** --- persisted docs --- */
  final case class UserDoc(
      _id: ObjectId = new ObjectId(),
      email: String,
      email_norm: String,
      passwordHash: String,
      userid: String,
      plan: Plan,
      status: SubscriptionStatus,
      displayName: Option[String],
      prefs: List[String] = Nil,
      createdAt: Instant = Instant.now(),
      emailVerified: Boolean = false,
      verificationTokenHash: Option[String] = None,
      verificationExpires: Option[Instant] = None // consider cron to clean up expired tokens
  )

  object UserDoc:
    import io.circe.{Codec, Decoder, Encoder, HCursor}
    import io.circe.generic.semiauto.*
    import mongo4cats.circe.*
    given encUserDoc: Encoder[UserDoc] = deriveEncoder[UserDoc]
    given decUserDoc: Decoder[UserDoc] = (c: HCursor) =>
      for
        id                    <- c.downField("_id").as[ObjectId]
        email                 <- c.downField("email").as[String]
        emailNorm             <- c.downField("email_norm").as[String]
        passwordHash          <- c.downField("passwordHash").as[String]
        userid                <- c.downField("userid").as[String]
        plan                  <- c.downField("plan").as[Plan]
        status                <- c.downField("status").as[SubscriptionStatus]
        displayName           <- c.downField("displayName").as[Option[String]]
        prefs                 <- c.downField("prefs").as[Option[List[String]]].map(_.getOrElse(Nil))
        createdAt             <- c.downField("createdAt").as[Instant]
        emailVerified         <- c.downField("emailVerified").as[Option[Boolean]].map(_.getOrElse(false))
        verificationTokenHash <- c.downField("verificationTokenHash").as[Option[String]]
        verificationExpires   <- c.downField("verificationExpires").as[Option[Instant]]
      yield UserDoc(
        id,
        email,
        emailNorm,
        passwordHash,
        userid,
        plan,
        status,
        displayName,
        prefs,
        createdAt,
        emailVerified,
        verificationTokenHash,
        verificationExpires
      )
    given Codec[UserDoc] = Codec.from(decUserDoc, encUserDoc)

  final case class UserProfileDoc(
      userid: String,
      avatarUrl: Option[String] = None,
      favGenres: List[String] = Nil,
      locale: Option[String] = Some("en_US")
  )

  // ── Which gateway is backing this record (future‑proofing) ──
  enum BillingGateway derives CanEqual:
    case Helcim, Stripe

  object BillingGateway:
    given Encoder[BillingGateway] =
      Encoder.encodeString.contramap {
        case BillingGateway.Helcim => "helcim"
        case BillingGateway.Stripe => "stripe"
      }
    given Decoder[BillingGateway] =
      Decoder.decodeString.emap {
        case "helcim" => Right(BillingGateway.Helcim)
        case "stripe" => Right(BillingGateway.Stripe)
        case other    => Left(s"unsupported gateway: $other")
      }
    given codecBillingGateway(using
        dec: Decoder[BillingGateway],
        enc: Encoder[BillingGateway]
    ): Codec[BillingGateway] =
      io.circe.Codec.from(dec, enc)

  // ── Helcim account refs attached to a user ──
  // customerId: Helcim “customer” object id
  // defaultCardToken / defaultBankToken: tokenized payment methods (PCI‑safe)
  final case class HelcimAccount(
      customerId: String,
      defaultCardToken: Option[String] = None,
      defaultBankToken: Option[String] = None
  )

  object HelcimAccount:
    given Codec[HelcimAccount] = deriveCodec

  // ── Snapshot of a Helcim Recurring subscription ──
  // Keep `status` as String to avoid coupling to Helcim’s status vocabulary.
  final case class HelcimSubSnapshot(
      subscriptionId: String, // /v2/subscriptions id
      planId: String,         // /v2/payment-plans id
      status: String,         // e.g. "active", "paused", ...
      nextBillAt: Option[Instant] = None,
      amountCents: Long,
      currency: String
  )

  object HelcimSubSnapshot:
    given Codec[HelcimSubSnapshot] = deriveCodec

  final case class Address(
      line1: String,
      line2: Option[String] = None,
      city: String,
      state: String,
      postalCode: String,
      country: String
  )

  object Address:
    given Codec[Address] = deriveCodec

  final case class BillingInfo(
      userId: String,
      gateway: BillingGateway = BillingGateway.Helcim,
      helcim: HelcimAccount, // required when gateway=Helcim
      address: Address,
      subscription: Option[HelcimSubSnapshot] = None,
      updatedAt: Instant = Instant.now()
  )

  object BillingInfo:
    given Codec[BillingInfo] = deriveCodec

  // ---------- request / response ----------
  final case class LoginRequest(email: String, password: String)
  final case class LoginOK(userid: String, tokens: TokenPair)
  final case class SignupOK(userid: String)

  /** Public view of a user, omitting sensitive fields */
  final case class UserInfo(userid: String, email: String, plan: String, displayName: Option[String])

  // ===== Requests / responses ===================================================
  final case class RegisterReq(email: String, password: String)
  final case class LoginReq(email: String, password: String)
  final case class TokenPair(access: String, refresh: String)
