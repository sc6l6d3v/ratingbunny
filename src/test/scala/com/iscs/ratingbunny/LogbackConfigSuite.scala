package com.iscs.ratingbunny

import ch.qos.logback.classic.{Level, LoggerContext}
import ch.qos.logback.classic.joran.JoranConfigurator
import munit.FunSuite
import org.slf4j.Logger

/** Loads the production logback.xml into a fresh context and checks which levels LOG_LEVEL yields. */
class LogbackConfigSuite extends FunSuite:

  private val switched = List(Logger.ROOT_LOGGER_NAME, "org.mongodb.driver.protocol.command", "mongo4cats", "emil", "com.sun.mail")
  private val fixed    = List("org.mongodb.driver" -> Level.INFO, "org.http4s.client" -> Level.INFO)

  private def levelsWith(props: Map[String, String]): Map[String, Level] =
    val ctx = LoggerContext()
    props.foreach((k, v) => ctx.putProperty(k, v))
    val conf = JoranConfigurator()
    conf.setContext(ctx)
    conf.doConfigure(getClass.getResource("/logback.xml"))
    try (switched ++ fixed.map(_._1)).map(n => n -> ctx.getLogger(n).getEffectiveLevel).toMap
    finally ctx.stop()

  test("without LOG_LEVEL every logger stays at INFO, so nothing logs document bodies or tokens"):
    val levels = levelsWith(Map.empty)
    switched.foreach(n => assertEquals(levels(n), Level.INFO, n))
    fixed.foreach((n, l) => assertEquals(levels(n), l, n))

  test("LOG_LEVEL=DEBUG restores the full debug logging"):
    val levels = levelsWith(Map("LOG_LEVEL" -> "DEBUG"))
    switched.foreach(n => assertEquals(levels(n), Level.DEBUG, n))
    fixed.foreach((n, l) => assertEquals(levels(n), l, n))
