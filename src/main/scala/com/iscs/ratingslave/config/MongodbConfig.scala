package com.iscs.ratingslave.config

import com.mongodb.ReadPreference
import org.mongodb.scala.{ConnectionString, MongoClientSettings, MongoCredential}

case class MongodbConfig(url: String, isReadOnly: Boolean = false) {
  val connection = new ConnectionString(url)

  val credentials: MongoCredential = connection.getCredential

  val useSSL: Boolean = connection.getSslEnabled != null

  val isReplicaSet: Boolean = connection.getRequiredReplicaSetName != null

  private val baseSettings: MongoClientSettings.Builder = MongoClientSettings.builder()
    .applyToConnectionPoolSettings(b => b.minSize(128).maxSize(256))
    .applyConnectionString(connection)
    .readPreference(ReadPreference.secondaryPreferred)
    .credential(credentials)

  val settings: MongoClientSettings = if (useSSL)
    baseSettings
      .applyToSslSettings(b => b.enabled(useSSL))
      .build()
  else
    baseSettings
      .build()
}
