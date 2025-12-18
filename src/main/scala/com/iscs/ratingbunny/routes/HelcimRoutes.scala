package com.iscs.ratingbunny.routes

import cats.effect.kernel.Ref
import cats.effect.Async
import cats.implicits.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.parser.parse
import io.circe.syntax.*
import io.circe.{Decoder, Encoder, HCursor, Json, Printer}
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.dsl.Http4sDsl
import org.http4s.{EntityDecoder, EntityEncoder, Header, Headers, HttpRoutes, Method, Request}
import org.http4s.client.Client
import org.http4s.implicits.uri
import org.typelevel.ci.CIString

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant

object HelcimRoutes:

  final case class InitFrontendRequest(currency: String)
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

  final case class ConfirmTokenRequest(checkoutToken: String, eventMessage: String)
  object ConfirmTokenRequest:
    given Decoder[ConfirmTokenRequest] = deriveDecoder

  final case class StoredSecret(secretToken: String, expiresAt: Instant)

  private def sha256Hex(s: String): String =
    val md    = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(s.getBytes(StandardCharsets.UTF_8))
    bytes.map("%02x".format(_)).mkString

  private def extractTxnDataAndHash(eventMsgJson: Json): Either[String, (Json, String)] =
    val c: HCursor = eventMsgJson.hcursor

    val shapeA =
      for
        dataObj <- c.downField("data").focus.toRight("missing data")
        txn     <- dataObj.hcursor.downField("data").focus.toRight("missing data.data")
        hash    <- dataObj.hcursor.get[String]("hash").leftMap(_.message)
      yield (txn, hash)

    val shapeB =
      for
        txn  <- c.downField("data").focus.toRight("missing data")
        hash <- c.get[String]("hash").leftMap(_.message)
      yield (txn, hash)

    shapeA.orElse(shapeB)

  def httpRoutes[F[_]: Async](client: Client[F], helcimApiToken: String, secrets: Ref[F, Map[String, StoredSecret]]): HttpRoutes[F] =
    val dsl = Http4sDsl[F]; import dsl.*

    given EntityDecoder[F, InitFrontendRequest]            = jsonOf
    given EntityEncoder[F, HelcimInitializeRequest]        = jsonEncoderOf
    given EntityDecoder[F, HelcimInitializeResponse]       = jsonOf
    given EntityDecoder[F, ConfirmTokenRequest]            = jsonOf

    val initRoutes = HttpRoutes.of[F]:
      case req @ POST -> Root / "helcim" / "initialize" =>
        for
          in <- req.as[InitFrontendRequest].handleError(_ => InitFrontendRequest("USD"))
          helcimBody = HelcimInitializeRequest(
            paymentType = "verify",
            amount = BigDecimal(0),
            currency = in.currency,
            paymentMethod = Some("cc")
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
          _ <- secrets.update(_ + (helcimResp.checkoutToken -> StoredSecret(helcimResp.secretToken, exp)))

          out  = Json.obj("checkoutToken" -> helcimResp.checkoutToken.asJson)
          resp <- Ok(out)
        yield resp

    val confirmRoutes = HttpRoutes.of[F]:
      case req @ POST -> Root / "helcim" / "confirm-token" =>
        (for
          in <- req.as[ConfirmTokenRequest]
          storedOpt <- secrets.get.map(_.get(in.checkoutToken))
          stored <- Async[F].fromOption(
            storedOpt.filter(_.expiresAt.isAfter(Instant.now())),
            new RuntimeException("Unknown/expired checkoutToken")
          )
          msgJson <- Async[F].fromEither(parse(in.eventMessage).leftMap(err => new RuntimeException(err.message)))
          (txnJson, helcimHash) <- Async[F].fromEither(extractTxnDataAndHash(msgJson).leftMap(new RuntimeException(_)))
          cleanedTxn = Printer.noSpaces.print(txnJson)
          yourHash   = sha256Hex(cleanedTxn + stored.secretToken)
          _ <- Async[F].raiseWhen(!yourHash.equalsIgnoreCase(helcimHash))(
            new RuntimeException("Invalid Helcim hash")
          )
          _ <- Async[F].fromEither(
            txnJson.hcursor.get[String]("cardToken").leftMap(df => new RuntimeException(df.message))
          )
          _ <- secrets.update(_ - in.checkoutToken)
          resp <- Ok(Json.obj("ok" -> true.asJson, "cardTokenStored" -> true.asJson))
        yield resp).handleErrorWith(e => BadRequest(Json.obj("error" -> e.getMessage.asJson)))

    initRoutes <+> confirmRoutes
