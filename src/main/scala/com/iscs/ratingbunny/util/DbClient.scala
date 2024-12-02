package com.iscs.ratingbunny.util

import cats.effect.{Async, Resource, Sync}
import com.iscs.ratingbunny.config.MongodbConfig
import mongo4cats.client.MongoClient

class DbClient[F[_]: Sync: Async](val config: MongodbConfig) {
  val dbResource: Resource[F, MongoClient[F]] = MongoClient.create(config.settings)
}

final case class ConnectionPoolConfig(
    minPoolSize: Int,
    maxPoolSize: Int,
    maxWaitTimeMS: Long,
    maxConnectionLifeTimeMS: Long,
    maxConnectionIdleTimeMS: Long,
    maxConnecting: Int
)

object DbClient {
  def fromUrl(envVar: String): MongodbConfig = {
    val host                          = sys.env.getOrElse(envVar, "localhost")
    val isReadOnly                    = sys.env.getOrElse("MONGORO", "false").toBoolean
    val minPoolSize: Int              = sys.env.getOrElse("MONGO_MIN_POOL_SIZE", "5").toInt
    val maxPoolSize: Int              = sys.env.getOrElse("MONGO_MAX_POOL_SIZE", "50").toInt
    val maxWaitTimeMS: Long           = sys.env.getOrElse("MONGO_MAX_WAIT_TIME_MS", "120000").toLong       // 2 minutes
    val maxConnectionLifeTimeMS: Long = sys.env.getOrElse("MONGO_MAX_CONN_LIFE_TIME_MS", "3600000").toLong // 1 hour
    val maxConnectionIdleTimeMS: Long = sys.env.getOrElse("MONGO_MAX_CONN_IDLE_TIME_MS", "1800000").toLong // 30 minutes
    val maxConnecting: Int            = sys.env.getOrElse("MONGO_MAX_CONNECTING", "10").toInt
    MongodbConfig(
      host,
      isReadOnly,
      ConnectionPoolConfig(minPoolSize, maxPoolSize, maxWaitTimeMS, maxConnectionLifeTimeMS, maxConnectionIdleTimeMS, maxConnecting)
    )
  }
}
