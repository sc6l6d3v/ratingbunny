package com.iscs.ratingbunny.messaging

import cats.implicits.*
import cats.effect.{Async, Resource}
import dev.profunktor.fs2rabbit.config.declaration.{DeclarationExchangeConfig, DeclarationQueueConfig}
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model.*
import io.circe.syntax.*

object EmailPublisher:
  private val Exchange: ExchangeName        = ExchangeName("rb.email")
  private val Queue: QueueName              = QueueName("rb.email.jobs")
  private val ContactRoutingKey: RoutingKey = RoutingKey("email.contact")
  private val VerifyRoutingKey: RoutingKey  = RoutingKey("email.verify_signup")

  private def setup[F[_]: Async](client: RabbitClient[F]): Resource[F, Unit] =
    client.createConnectionChannel.evalMap { implicit ch =>
      for
        _ <- client.declareExchange(DeclarationExchangeConfig.default(Exchange, ExchangeType.Topic).withDurable)
        _ <- client.declareQueue(DeclarationQueueConfig.default(Queue).withDurable)
        _ <- client.bindQueue(Queue, Exchange, ContactRoutingKey)
        _ <- client.bindQueue(Queue, Exchange, VerifyRoutingKey)
      yield ()
    }

  def make[F[_]: Async](client: RabbitClient[F]): Resource[F, EmailJob => F[Unit]] =
    setup(client).flatMap { _ =>
      client.createConnectionChannel.evalMap { implicit ch =>
        val flag = PublishingFlag(mandatory = true)
        val onReturn: PublishReturn => F[Unit] =
          pr => Async[F].delay(System.err.println(s"UNROUTABLE publish: $pr"))
        for
          publishContact <- client.createPublisherWithListener[String](Exchange, ContactRoutingKey, flag, onReturn)
          publishVerify  <- client.createPublisherWithListener[String](Exchange, VerifyRoutingKey, flag, onReturn)
        yield (job: EmailJob) =>
          val payload = job.asJson.noSpaces
          job.kind match
            case EmailJob.KindContact      => publishContact(payload)
            case EmailJob.KindVerifySignup => publishVerify(payload)
            case other =>
              Async[F].raiseError(new IllegalArgumentException(s"Unsupported email job kind: $other"))
      }
    }
