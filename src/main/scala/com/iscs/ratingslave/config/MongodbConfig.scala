package com.iscs.ratingslave.config

import com.mongodb.ReadPreference
import org.mongodb.scala.{ConnectionString, MongoClientSettings, MongoCredential}

case class MongodbConfig(url: String, isReadOnly: Boolean = false) {
  val connection = new ConnectionString(url)

  val credentials: MongoCredential = connection.getCredential

  val useSSL = connection.getSslEnabled != null

  val isReplicaSet = connection.getRequiredReplicaSetName != null

  val baseSettings = MongoClientSettings.builder()
    .applyToConnectionPoolSettings(b => b.minSize(128).maxSize(256))
    .applyConnectionString(connection)
    .readPreference(ReadPreference.secondaryPreferred)
    .credential(credentials)

  val settings = if (useSSL)
    baseSettings
      .applyToSslSettings(b => b.enabled(useSSL))
      .build()
  else
    baseSettings
      .build()
}
