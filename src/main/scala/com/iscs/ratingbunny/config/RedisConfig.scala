package com.iscs.ratingbunny.config

import cats.effect.Resource
import cats.effect.kernel.Sync
import dev.profunktor.redis4cats.{Redis, RedisCommands}
import dev.profunktor.redis4cats.connection.*
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.MkRedis

class RedisConfig[F[_]: MkRedis: Sync]() {
  private val redisHost                               = sys.env.getOrElse("REDISHOST", "localhost")
  private val pwd                                     = sys.env.getOrElse("REDISKEY", "NOREDISKEY")
  private val uriString                               = s"redis://$pwd@$redisHost"
  private val stringCodec: RedisCodec[String, String] = RedisCodec.Utf8
  val resource: Resource[F, RedisCommands[F, String, String]] = for {
    uri <- Resource.eval(RedisURI.make[F](uriString))
    cli <- RedisClient[F].fromUri(uri)
    rd  <- Redis[F].fromClient(cli, stringCodec)
  } yield rd
}
