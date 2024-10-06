package com.iscs.ratingbunny.config

import com.mongodb.ReadPreference
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import scala.concurrent.duration.MILLISECONDS

class MongodbConfigTest extends AnyFunSuite with Matchers {

  test("MongodbConfig should parse the URL correctly") {
    val url    = "mongodb://localhost:27017"
    val config = MongodbConfig(url)

    config.url shouldEqual url
    config.isReadOnly shouldBe false
    config.isReplicaSet shouldBe false
  }

  test("MongodbConfig should identify read-only mode") {
    val url    = "mongodb://localhost:27017"
    val config = MongodbConfig(url, isReadOnly = true)

    config.isReadOnly shouldBe true
    config.settings.getReadPreference shouldBe ReadPreference.secondaryPreferred
  }

  test("MongodbConfig should detect SSL usage") {
    val url    = "mongodb://localhost:27017/?ssl=true"
    val config = MongodbConfig(url)

    config.settings.getSslSettings.isEnabled shouldBe true
  }

  test("MongodbConfig should detect a replica set") {
    val url    = "mongodb://localhost:27017/?replicaSet=myReplicaSet"
    val config = MongodbConfig(url)

    config.isReplicaSet shouldBe true
  }

  test("MongodbConfig should handle credentials correctly") {
    val url    = "mongodb://user:pass@localhost:27017"
    val config = MongodbConfig(url)

    val credentials = config.settings.getCredential
    credentials should not be null
    credentials.getUserName shouldEqual "user"
  }

  test("MongodbConfig should apply connection pool settings") {
    val url    = "mongodb://localhost:27017"
    val config = MongodbConfig(url)

    val connectionPoolSettings = config.settings.getConnectionPoolSettings
    connectionPoolSettings.getMinSize shouldEqual 128
    connectionPoolSettings.getMaxSize shouldEqual 256
    connectionPoolSettings.getMaxWaitTime(MILLISECONDS) shouldEqual 120000            // 2 minutes
    connectionPoolSettings.getMaxConnectionLifeTime(MILLISECONDS) shouldEqual 3600000 // 1 hour
    connectionPoolSettings.getMaxConnectionIdleTime(MILLISECONDS) shouldEqual 600000  // 10 minutes
    connectionPoolSettings.getMaxConnecting shouldEqual 10
  }
}
