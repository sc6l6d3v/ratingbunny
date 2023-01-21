import Dependencies._

lazy val root = (project in file("."))
  .settings(
    organization := "com.iscs",
    name := "ratingslave",
    version := "1.0",
    scalaVersion := "2.13.8",
    scalacOptions ++= Seq("-target:17"),
    libraryDependencies ++= Seq(
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
      scalaTest,
      logback.classic,
      logback.logging,
      cats.retry,
      cats.log4cats,
      jsoup.base,
    ),
    addCompilerPlugin("org.typelevel" %% "kind-projector"     % "0.10.3"),
    addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.1"),
    Revolver.enableDebugging(5061, true)
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
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}
