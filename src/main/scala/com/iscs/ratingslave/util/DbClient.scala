package com.iscs.ratingslave.util

import cats.effect.{Async, Resource, Sync}
import com.iscs.ratingslave.config.MongodbConfig
import mongo4cats.client.MongoClient

class DbClient[F[_]: Sync : Async](val config: MongodbConfig) {
  val dbResource: Resource[F, MongoClient[F]] = MongoClient.create(config.settings)
}

object DbClient {
  def fromUrl(envVar: String): MongodbConfig = {
    val host = sys.env.getOrElse(envVar, "localhost")
    val isReadOnly = sys.env.getOrElse("MONGORO", "false").toBoolean
    MongodbConfig(host, isReadOnly)
  }
}
