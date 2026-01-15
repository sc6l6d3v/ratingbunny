package com.iscs.ratingbunny.config

import cats.effect.{Async, Resource}
import dev.profunktor.fs2rabbit.config.Fs2RabbitConfig
import dev.profunktor.fs2rabbit.interpreter.RabbitClient

final case class RabbitConfig(
    host: String,
    port: Int,
    virtualHost: String,
    username: String,
    password: String,
    ssl: Boolean
):
  def toFs2RabbitConfig: Fs2RabbitConfig =
    Fs2RabbitConfig(
      host = host,
      port = port,
      virtualHost = virtualHost,
      username = username,
      password = password,
      ssl = ssl
    )

object RabbitConfig:
  def fromEnv(): RabbitConfig =
    RabbitConfig(
      host = sys.env.getOrElse("RABBIT_HOST", "localhost"),
      port = sys.env.getOrElse("RABBIT_PORT", "5672").toInt,
      virtualHost = sys.env.getOrElse("RABBIT_VHOST", "/"),
      username = sys.env.getOrElse("RABBIT_USERNAME", "guest"),
      password = sys.env.getOrElse("RABBIT_PASSWORD", "guest"),
      ssl = sys.env.getOrElse("RABBIT_SSL", "false").toBoolean
    )

  def resource[F[_]: Async](config: RabbitConfig): Resource[F, RabbitClient[F]] =
    RabbitClient[F].resource(config.toFs2RabbitConfig)
