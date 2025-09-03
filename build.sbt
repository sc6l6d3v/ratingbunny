import Dependencies.{test, *}

ThisBuild / fork             := true
ThisBuild / version          := "1.0"
ThisBuild / scalaVersion     := "3.5.0"
ThisBuild / name             := "ratingbunny"
ThisBuild / organizationName := "com.iscs"
ThisBuild / javacOptions ++= Seq("--release", "21") // For Java 21

lazy val root = (project in file("."))
  .settings(
    name := "ratingbunny",
    libraryDependencies ++= Seq(
      http4s.client,
      http4s.server,
      http4s.dsl,
      http4s.circe,
      fs2.core,
      fs2.io,
      jwt.circe,
      bcrypt.core,
      circe.circeCore,
      circe.circeGeneric,
      circe.circeParser,
      emailSvc.client,
      mongo4cats.core,
      mongo4cats.circe,
      mongo4cats.embedded,
      mongodb.driver,
      test.scalatest,
      munit.catseffect,
      redis4cats.core,
      redis4cats.stream,
      scalamock.core,
      logback.classic,
      logback.logging,
      jsoup.base
    ),
    Compile / mainClass      := Some("com.iscs.ratingbunny.Main"),
    coverageExcludedPackages := "com\\.iscs\\.ratingbunny\\.(dslparams|model).*",
    Revolver.enableDebugging(5061, suspend = false)
  )

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding",
  "UTF-8",
  "-feature",
  "--release",
  "21"
  // "-Xfatal-warnings",
)

addCommandAlias(
  "format",
  "scalafmt; scalafmtSbt; Test / scalafmt"
)

addCommandAlias(
  "formatCheck",
  "scalafmtCheck; scalafmtSbtCheck; Test  / scalafmtCheck"
)

addCommandAlias(
  "validate",
  "formatCheck; coverage; test; coverageReport; coverageAggregate; coverageOff"
)

assembly / assemblyMergeStrategy := {
  case PathList("META-INF", "services", xs @ _*) => MergeStrategy.concat
  case PathList("META-INF", xs @ _*)             => MergeStrategy.discard
  case "io.netty.versions.properties"            => MergeStrategy.discard
  case "reference.conf"                          => MergeStrategy.concat
  case x                                         => MergeStrategy.first
}
