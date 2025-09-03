import sbt.*

object Dependencies {
  object V {
    val bcryptVersion     = "4.3.0"
    val circeVersion      = "0.14.6"
    val emailSvc          = "1.0"
    val fs2Version        = "3.10.2"
    val Http4sVersion     = "0.23.26"
    val jsoupVersion      = "1.17.2"
    val LogbackVersion    = "1.5.3"
    val loggingVersion    = "3.9.5"
    val scalamockVersion  = "6.0.0"
    val mongo4catsVersion = "0.7.12"
    val mongoScalaVersion = "5.4.0"
    val munitVersion      = "2.0.0"
    val redis4catsVersion = "2.0.0-1-0f092da-SNAPSHOT"
    val scalaTestVersion  = "3.2.18"
    val scalacheckVersion = "1.17.0"
    val jwtCirceVersion   = "11.0.0"
  }

  object emailSvc {
    val client = "com.iscs" %% "emailpoc" % V.emailSvc
  }

  object http4s {
    val client = "org.http4s" %% "http4s-ember-client" % V.Http4sVersion
    val server = "org.http4s" %% "http4s-ember-server" % V.Http4sVersion
    val circe  = "org.http4s" %% "http4s-circe"        % V.Http4sVersion
    val dsl    = "org.http4s" %% "http4s-dsl"          % V.Http4sVersion
  }

  object fs2 {
    val core = "co.fs2" %% "fs2-core" % V.fs2Version
    val io   = "co.fs2" %% "fs2-io"   % V.fs2Version
  }

  object bcrypt {
    val core = "com.github.t3hnar" % "scala-bcrypt_2.13" % V.bcryptVersion
  }

  object circe {
    val circeCore    = "io.circe" %% "circe-core"    % V.circeVersion
    val circeParser  = "io.circe" %% "circe-parser"  % V.circeVersion
    val circeGeneric = "io.circe" %% "circe-generic" % V.circeVersion
  }

  object logback {
    val logging = "com.typesafe.scala-logging" %% "scala-logging"   % V.loggingVersion
    val classic = "ch.qos.logback"              % "logback-classic" % V.LogbackVersion
  }

  object jsoup {
    val base = "org.jsoup" % "jsoup" % V.jsoupVersion
  }

  object redis4cats {
    val core     = "dev.profunktor" %% "redis4cats-effects"  % V.redis4catsVersion
    val stream   = "dev.profunktor" %% "redis4cats-streams"  % V.redis4catsVersion
    val log4cats = "dev.profunktor" %% "redis4cats-log4cats" % V.redis4catsVersion
  }

  object scalamock {
    val core = "org.scalamock" %% "scalamock" % V.scalamockVersion % Test
  }

  object mongodb {
    val driver = "org.mongodb.scala" %% "mongo-scala-driver" % V.mongoScalaVersion cross CrossVersion.for3Use2_13
  }

  object mongo4cats {
    val core     = "io.github.kirill5k" %% "mongo4cats-core"     % V.mongo4catsVersion
    val circe    = "io.github.kirill5k" %% "mongo4cats-circe"    % V.mongo4catsVersion
    val embedded = "io.github.kirill5k" %% "mongo4cats-embedded" % V.mongo4catsVersion % Test
  }

  object munit {
    val catseffect = "org.typelevel" %% "munit-cats-effect" % V.munitVersion % Test
  }

  object test {
    val scalatest  = "org.scalatest"  %% "scalatest"  % V.scalaTestVersion  % Test
    val scalacheck = "org.scalacheck" %% "scalacheck" % V.scalacheckVersion % Test
  }

  object jwt {
    val circe = "com.github.jwt-scala" %% "jwt-circe" % V.jwtCirceVersion
  }
}
