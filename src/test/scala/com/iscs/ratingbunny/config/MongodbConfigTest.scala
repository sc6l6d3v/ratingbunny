package com.iscs.ratingbunny.config

import com.iscs.ratingbunny.util.ConnectionPoolConfig
import com.mongodb.ReadPreference
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.MILLISECONDS

class MongodbConfigTest extends AnyFunSuite with Matchers {

  val minPoolSize: Int              = sys.env.getOrElse("MONGO_MIN_POOL_SIZE", "5").toInt
  val maxPoolSize: Int              = sys.env.getOrElse("MONGO_MAX_POOL_SIZE", "50").toInt
  val maxWaitTimeMS: Long           = sys.env.getOrElse("MONGO_MAX_WAIT_TIME_MS", "120000").toLong       // 2 minutes
  val maxConnectionLifeTimeMS: Long = sys.env.getOrElse("MONGO_MAX_CONN_LIFE_TIME_MS", "3600000").toLong // 1 hour
  val maxConnectionIdleTimeMS: Long = sys.env.getOrElse("MONGO_MAX_CONN_IDLE_TIME_MS", "1800000").toLong // 30 minutes
  val maxConnecting: Int            = sys.env.getOrElse("MONGO_MAX_CONNECTING", "10").toInt
  private val cpConfig =
    ConnectionPoolConfig(minPoolSize, maxPoolSize, maxWaitTimeMS, maxConnectionLifeTimeMS, maxConnectionIdleTimeMS, maxConnecting)

  test("MongodbConfig should parse the URL correctly") {
    val url    = "mongodb://localhost:27017"
    val config = MongodbConfig(url, cpConfig = cpConfig)

    config.url shouldEqual url
    config.isReadOnly shouldBe false
    config.isReplicaSet shouldBe false
  }

  test("MongodbConfig should identify read-only mode") {
    val url    = "mongodb://localhost:27017"
    val config = MongodbConfig(url, isReadOnly = true, cpConfig = cpConfig)

    config.isReadOnly shouldBe true
    config.settings.getReadPreference shouldBe ReadPreference.secondaryPreferred
  }

  test("MongodbConfig should detect SSL usage") {
    val url    = "mongodb://localhost:27017/?ssl=true"
    val config = MongodbConfig(url, cpConfig = cpConfig)

    config.settings.getSslSettings.isEnabled shouldBe true
  }

  test("MongodbConfig should detect a replica set") {
    val url    = "mongodb://localhost:27017/?replicaSet=myReplicaSet"
    val config = MongodbConfig(url, cpConfig = cpConfig)

    config.isReplicaSet shouldBe true
  }

  test("MongodbConfig should handle credentials correctly") {
    val url    = "mongodb://user:pass@localhost:27017"
    val config = MongodbConfig(url, cpConfig = cpConfig)

    val credentials = config.settings.getCredential
    credentials should not be null
    credentials.getUserName shouldEqual "user"
  }

  test("MongodbConfig should apply connection pool settings") {
    val url    = "mongodb://localhost:27017"
    val config = MongodbConfig(url, cpConfig = cpConfig)

    val connectionPoolSettings = config.settings.getConnectionPoolSettings
    connectionPoolSettings.getMinSize shouldEqual 5
    connectionPoolSettings.getMaxSize shouldEqual 50
    connectionPoolSettings.getMaxWaitTime(MILLISECONDS) shouldEqual 120000            // 2 minutes
    connectionPoolSettings.getMaxConnectionLifeTime(MILLISECONDS) shouldEqual 3600000 // 1 hour
    connectionPoolSettings.getMaxConnectionIdleTime(MILLISECONDS) shouldEqual 1800000 // 30 minutes
    connectionPoolSettings.getMaxConnecting shouldEqual 10
  }
}
