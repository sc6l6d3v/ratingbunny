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

  def fromSettings[F[_]](settings: MongoClientSettings)(
    implicit F: Sync[F]): Resource[F, MongoClient] = {
    Resource.make(F.delay(MongoClient(settings)))(client =>
      F.delay(client.close()))
  }
}
