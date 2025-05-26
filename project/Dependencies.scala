import sbt.*

object Dependencies {
  object Versions {
    val bcryptVersion     = "4.3.0"
    val circeVersion      = "0.14.6"
    val fs2Version        = "3.10.2"
    val Http4sVersion     = "0.23.26"
    val jsoupVersion      = "1.17.2"
    val LogbackVersion    = "1.5.3"
    val loggingVersion    = "3.9.5"
    val scalamockVersion  = "6.0.0"
    val mongo4catsVersion = "0.7.12"
    val mongoScalaVersion = "5.4.0"
    val munitVersion      = "2.0.0"
    val scalaTestVersion  = "3.2.18"
    val scalacheckVersion = "1.17.0"
  }

  object http4s {
    val client = "org.http4s" %% "http4s-ember-client" % Versions.Http4sVersion
    val server = "org.http4s" %% "http4s-ember-server" % Versions.Http4sVersion
    val circe  = "org.http4s" %% "http4s-circe"        % Versions.Http4sVersion
    val dsl    = "org.http4s" %% "http4s-dsl"          % Versions.Http4sVersion
  }

  object fs2 {
    val core = "co.fs2" %% "fs2-core" % Versions.fs2Version
    val io   = "co.fs2" %% "fs2-io"   % Versions.fs2Version
  }

  object bcrypt {
    val core = "com.github.t3hnar" % "scala-bcrypt_2.13" % Versions.bcryptVersion
  }

  object circe {
    val circeCore    = "io.circe" %% "circe-core"    % Versions.circeVersion
    val circeParser  = "io.circe" %% "circe-parser"  % Versions.circeVersion
    val circeGeneric = "io.circe" %% "circe-generic" % Versions.circeVersion
  }

  object logback {
    val logging = "com.typesafe.scala-logging" %% "scala-logging"   % Versions.loggingVersion
    val classic = "ch.qos.logback"              % "logback-classic" % Versions.LogbackVersion
  }

  object jsoup {
    val base = "org.jsoup" % "jsoup" % Versions.jsoupVersion
  }

  object scalamock {
    val core = "org.scalamock" %% "scalamock" % Versions.scalamockVersion % Test
  }

  object mongodb {
    val driver = "org.mongodb.scala" %% "mongo-scala-driver" % Versions.mongoScalaVersion cross CrossVersion.for3Use2_13
  }

  object mongo4cats {
    val core     = "io.github.kirill5k" %% "mongo4cats-core"     % Versions.mongo4catsVersion
    val circe    = "io.github.kirill5k" %% "mongo4cats-circe"    % Versions.mongo4catsVersion
    val embedded = "io.github.kirill5k" %% "mongo4cats-embedded" % Versions.mongo4catsVersion % Test
  }

  object munit {
    val catseffect = "org.typelevel" %% "munit-cats-effect" % Versions.munitVersion % Test
  }

  object test {
    val scalatest  = "org.scalatest"  %% "scalatest"  % Versions.scalaTestVersion  % Test
    val scalacheck = "org.scalacheck" %% "scalacheck" % Versions.scalacheckVersion % Test
  }
}
