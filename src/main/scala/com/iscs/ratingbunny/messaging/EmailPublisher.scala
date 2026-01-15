package com.iscs.ratingbunny.messaging

import cats.effect.{Async, Resource}
import com.rabbitmq.client.BuiltinExchangeType
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model.*
import io.circe.syntax.*

object EmailPublisher:
  val Exchange: ExchangeName     = ExchangeName("rb.email")
  val Queue: QueueName           = QueueName("rb.email.jobs")
  val ContactRoutingKey: RoutingKey = RoutingKey("email.contact")
  val VerifyRoutingKey: RoutingKey  = RoutingKey("email.verify_signup")

  def setup[F[_]: Async](client: RabbitClient[F]): Resource[F, Unit] =
    client.createConnectionChannel.evalMap { implicit ch =>
      Async[F].delay {
        ch.exchangeDeclare(Exchange.value, BuiltinExchangeType.TOPIC, true)
        ch.queueDeclare(Queue.value, true, false, false, null)
        ch.queueBind(Queue.value, Exchange.value, ContactRoutingKey.value)
        ch.queueBind(Queue.value, Exchange.value, VerifyRoutingKey.value)
      }
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
