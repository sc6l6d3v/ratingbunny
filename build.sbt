import Dependencies.{test, *}

ThisBuild / version := "1.0"
ThisBuild / scalaVersion := "2.13.10"
ThisBuild / organization := "com.iscs"
ThisBuild / name := "ratingslave"
ThisBuild / javacOptions     ++= Seq("--release", "17") // For Java 17
ThisBuild / scalacOptions    ++= Seq("--release", "17")

lazy val root = (project in file("."))
  .settings(
    name := "ratingslave",
    libraryDependencies ++= Seq(
      http4s.client,
      http4s.server,
      http4s.dsl,
      http4s.circe,
      fs2.core,
      fs2.io,
      circe.circeCore,
      circe.circeGeneric,
      circe.circeParser,
      mongo4cats.core,
      mongo4cats.circe,
      mongodb.driver,
      test.scalatest,
      logback.classic,
      logback.logging,
      jsoup.base,
    ),
    Compile / mainClass := Some("com.iscs.ratingslave.Main"),
    addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.1"),
    Revolver.enableDebugging(5061, suspend = true),
  )

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-language:higherKinds",
  "-language:postfixOps",
  "-feature"
  //"-Xfatal-warnings",
)

assembly / assemblyMergeStrategy := {
  case PathList("META-INF", "services", xs @ _*) => MergeStrategy.concat
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case "io.netty.versions.properties" => MergeStrategy.discard
  case "reference.conf" => MergeStrategy.concat
  case x => MergeStrategy.first
}
