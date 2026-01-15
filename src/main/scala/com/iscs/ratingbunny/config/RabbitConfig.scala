package com.iscs.ratingbunny.config

import cats.effect.{Async, Resource}
import dev.profunktor.fs2rabbit.config.Fs2RabbitConfig
import dev.profunktor.fs2rabbit.interpreter.RabbitClient

import scala.concurrent.duration.*

final case class RabbitConfig(
    host: String,
    port: Int,
    virtualHost: String,
    username: String,
    password: String,
    ssl: Boolean,
    connectionTimeout: FiniteDuration,
    requeueOnNack: Boolean,
    requeueOnReject: Boolean,
    internalQueueSize: Option[Int],
    requestedHeartbeat: FiniteDuration,
    automaticRecovery: Boolean,
    automaticTopologyRecovery: Boolean,
    clientProvidedConnectionName: Option[String]
):
  def toFs2RabbitConfig: Fs2RabbitConfig =
    Fs2RabbitConfig(
      host = host,
      port = port,
      virtualHost = virtualHost,
      connectionTimeout = connectionTimeout,
      ssl = ssl,
      username = Some(username),
      password = Some(password),
      requeueOnNack = requeueOnNack,
      requeueOnReject = requeueOnReject,
      internalQueueSize = internalQueueSize,
      requestedHeartbeat = requestedHeartbeat,
      automaticRecovery = automaticRecovery,
      automaticTopologyRecovery = automaticTopologyRecovery,
      clientProvidedConnectionName = clientProvidedConnectionName
    )

object RabbitConfig:
  private def toIntOption(value: String): Option[Int] =
    value.trim match
      case "" => None
      case other => other.toIntOption

  private def toDurationSeconds(value: String, default: FiniteDuration): FiniteDuration =
    value.toIntOption.map(_.seconds).getOrElse(default)

  def fromEnv(): RabbitConfig =
    RabbitConfig(
      host = sys.env.getOrElse("RABBIT_HOST", "localhost"),
      port = sys.env.getOrElse("RABBIT_PORT", "5672").toInt,
      virtualHost = sys.env.getOrElse("RABBIT_VHOST", "/"),
      username = sys.env.getOrElse("RABBIT_USERNAME", "guest"),
      password = sys.env.getOrElse("RABBIT_PASSWORD", "guest"),
      ssl = sys.env.getOrElse("RABBIT_SSL", "false").toBoolean,
      connectionTimeout = toDurationSeconds(sys.env.getOrElse("RABBIT_CONN_TIMEOUT_SECONDS", "10"), 10.seconds),
      requeueOnNack = sys.env.getOrElse("RABBIT_REQUEUE_ON_NACK", "true").toBoolean,
      requeueOnReject = sys.env.getOrElse("RABBIT_REQUEUE_ON_REJECT", "false").toBoolean,
      internalQueueSize = toIntOption(sys.env.getOrElse("RABBIT_INTERNAL_QUEUE_SIZE", "")),
      requestedHeartbeat = toDurationSeconds(
        sys.env.getOrElse("RABBIT_REQUESTED_HEARTBEAT_SECONDS", "60"),
        60.seconds
      ),
      automaticRecovery = sys.env.getOrElse("RABBIT_AUTOMATIC_RECOVERY", "true").toBoolean,
      automaticTopologyRecovery = sys.env.getOrElse("RABBIT_AUTOMATIC_TOPOLOGY_RECOVERY", "true").toBoolean,
      clientProvidedConnectionName = sys.env.get("RABBIT_CONNECTION_NAME")
    )

  def resource[F[_]: Async](config: RabbitConfig): Resource[F, RabbitClient[F]] =
    RabbitClient[F].resource(config.toFs2RabbitConfig)
