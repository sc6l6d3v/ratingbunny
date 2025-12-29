package com.iscs.ratingbunny.routes

import cats.effect.Async
import cats.implicits.*
import com.typesafe.scalalogging.Logger
import dev.profunktor.redis4cats.RedisCommands
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder, deriveCodec}
import io.circe.syntax.*
import io.circe.{Codec, Decoder, Encoder, Json, Printer}
import io.circe.parser.decode
import org.http4s.circe.CirceEntityCodec.circeEntityEncoder
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.dsl.Http4sDsl
import org.http4s.{EntityDecoder, EntityEncoder, Header, Headers, HttpRoutes, Method, Request}
import org.http4s.client.Client
import org.http4s.implicits.uri
import org.typelevel.ci.CIString

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import scala.concurrent.duration.*

object HelcimRoutes:

  private val L = Logger[this.type]

  final case class InitFrontendRequest(paymentType: String, amount: BigDecimal, currency: String, paymentMethod: String)
  object InitFrontendRequest:
    given Decoder[InitFrontendRequest] = deriveDecoder

  final case class HelcimInitializeRequest(
      paymentType: String,
      amount: BigDecimal,
      currency: String,
      paymentMethod: Option[String]
  )
  object HelcimInitializeRequest:
    given Encoder[HelcimInitializeRequest] = deriveEncoder

  final case class HelcimInitializeResponse(checkoutToken: String, secretToken: String)
  object HelcimInitializeResponse:
    given Decoder[HelcimInitializeResponse] = deriveDecoder

  final case class ConfirmTokenRequest(checkoutToken: String, data: Json, hash: String)
  object ConfirmTokenRequest:
    given Decoder[ConfirmTokenRequest] = deriveDecoder

  final case class StoredSecret(secretToken: String, expiresAt: Instant)
  object StoredSecret:
    given Codec[StoredSecret] = deriveCodec

  private def sha256Hex(s: String): String =
    val md    = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(s.getBytes(StandardCharsets.UTF_8))
    bytes.map("%02x".format(_)).mkString

  def httpRoutes[F[_]: Async](client: Client[F], helcimApiToken: String, redis: RedisCommands[F, String, String]): HttpRoutes[F] =
    val dsl = Http4sDsl[F]; import dsl.*

    given EntityDecoder[F, InitFrontendRequest]      = jsonOf
    given EntityEncoder[F, HelcimInitializeRequest]  = jsonEncoderOf
    given EntityDecoder[F, HelcimInitializeResponse] = jsonOf
    given EntityDecoder[F, ConfirmTokenRequest]      = jsonOf

    val initRoutes = HttpRoutes.of[F]:
      case req @ POST -> Root / "helcim" / "initialize" =>
        for
          in <- req.as[InitFrontendRequest].handleError(_ => InitFrontendRequest("purchase", 0.0d, "USD", "cc"))
          helcimBody = HelcimInitializeRequest(
            paymentType = in.paymentType,
            amount = in.amount,
            currency = in.currency,
            paymentMethod = Some(in.paymentMethod)
          )
          helcimReq = Request[F](
            method = Method.POST,
            uri = uri"https://api.helcim.com/v2/helcim-pay/initialize"
          ).withHeaders(
            Headers(
              Header.Raw(CIString("accept"), "application/json"),
              Header.Raw(CIString("content-type"), "application/json"),
              Header.Raw(CIString("api-token"), helcimApiToken)
            )
          ).withEntity(helcimBody)

          helcimResp <- client.expect[HelcimInitializeResponse](helcimReq)
          exp = Instant.now().plusSeconds(60 * 60)
          stored = StoredSecret(helcimResp.secretToken, exp)
          _ <- redis.setEx(helcimResp.checkoutToken, stored.asJson.noSpaces, 1.hour)

          out = Json.obj("secretToken" -> helcimResp.secretToken.asJson, "checkoutToken" -> helcimResp.checkoutToken.asJson)
          resp <- Ok(out)
        yield resp

    val confirmRoutes = HttpRoutes.of[F]:
      case req @ POST -> Root / "helcim" / "confirm-token" =>
        (for
          in   <- req.as[ConfirmTokenRequest]
          json <- redis
            .get(in.checkoutToken)
            .flatMap(opt => Async[F].fromOption(opt, new RuntimeException("Unknown/expired checkoutToken")))
          stored <- Async[F].fromEither(decode[StoredSecret](json).leftMap(df => new RuntimeException(df.getMessage)))
          _ <- Async[F].raiseWhen(stored.expiresAt.isBefore(Instant.now()))(
            new RuntimeException("Unknown/expired checkoutToken")
          )
          cleanedTxn = Printer.noSpaces.print(in.data)
          token      = stored.secretToken
          _ <- Async[F].delay(L.info(s""""request" helcimConfirm=$cleanedTxn secretToken=$token"""))
          yourHash = sha256Hex(cleanedTxn + token)
          _ <- Async[F].raiseWhen(!yourHash.equalsIgnoreCase(in.hash))(
            new RuntimeException("Invalid Helcim hash")
          )
          _ <- Async[F].fromEither(
            in.data.hcursor.get[String]("cardToken").leftMap(df => new RuntimeException(df.getMessage))
          )
          _    <- redis.del(in.checkoutToken).void
          resp <- Ok(Json.obj("ok" -> true.asJson, "cardTokenStored" -> true.asJson))
        yield resp).handleErrorWith(e => BadRequest(Json.obj("error" -> e.getMessage.asJson)))

    CORSSetup.methodConfig(initRoutes <+> confirmRoutes)
