package com.iscs.ratingbunny.config

import com.iscs.ratingbunny.util.ConnectionPoolConfig
import com.mongodb.ReadPreference
import com.typesafe.scalalogging.Logger
import org.mongodb.scala.connection.ConnectionPoolSettings
import org.mongodb.scala.{ConnectionString, MongoClientSettings, MongoCredential}
import scala.concurrent.duration.MILLISECONDS

case class MongodbConfig(url: String, isReadOnly: Boolean = false, cpConfig: ConnectionPoolConfig) {
  import cpConfig.*
  private val L          = Logger[this.type]
  private val connection = new ConnectionString(url)

  private val credentials: MongoCredential = connection.getCredential

  private val useSSL: Boolean = connection.getSslEnabled != null

  val isReplicaSet: Boolean = connection.getRequiredReplicaSetName != null

  L.info(
    "connection pool, minPoolSize: " + minPoolSize + ", maxPoolSize: " + maxPoolSize + ", maxWaitTimeMS: "
      + maxWaitTimeMS + ", maxConnectionLifeTimeMS: " + maxConnectionLifeTimeMS + ", maxConnectionIdleTimeMS: " +
      maxConnectionIdleTimeMS + ", maxConnecting: " + maxConnecting
  )

  private val connectionPoolSettings: ConnectionPoolSettings = ConnectionPoolSettings
    .builder()
    .minSize(minPoolSize)
    .maxSize(maxPoolSize)
    .maxWaitTime(maxWaitTimeMS, MILLISECONDS)                     // 2 minutes
    .maxConnectionLifeTime(maxConnectionLifeTimeMS, MILLISECONDS) // 1 hour
    .maxConnectionIdleTime(maxConnectionIdleTimeMS, MILLISECONDS) // 10 minutes
    .maxConnecting(maxConnecting)                                 // Increase the number of connections being established concurrently
    .build()

  private val baseSettings: MongoClientSettings.Builder = MongoClientSettings
    .builder()
    .applyToLoggerSettings(_.maxDocumentLength(5000))
    .applyToConnectionPoolSettings(_.applySettings(connectionPoolSettings))
    .applyConnectionString(connection)
    .readPreference(if (isReadOnly) ReadPreference.secondaryPreferred else ReadPreference.primaryPreferred)

  private val withCredentials = if (credentials == null) baseSettings else baseSettings.credential(credentials)

  val settings: MongoClientSettings =
    if (useSSL)
      withCredentials
        .applyToSslSettings(b => b.enabled(useSSL))
        .build()
    else
      withCredentials
        .build()
}
