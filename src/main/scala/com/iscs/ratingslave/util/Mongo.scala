package com.iscs.ratingslave.util

import org.mongodb.scala.MongoClientSettings
import cats.effect.{Resource, Sync}
import com.iscs.ratingslave.config.MongodbConfig
import org.mongodb.scala.MongoClient

object Mongo {
  def fromUrl(): MongodbConfig = {
    val host = sys.env.getOrElse("MONGOURI", "localhost")
    val isReadOnly = sys.env.getOrElse("MONGORO", "false").toBoolean
    MongodbConfig(host, isReadOnly)
  }

  def fromSettings[F[_]: Sync](settings: MongoClientSettings): Resource[F, MongoClient] = {
    Resource.make(Sync[F].delay(MongoClient(settings)))(client =>
      Sync[F].delay(client.close()))
  }
}
