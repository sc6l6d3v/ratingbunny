package com.iscs.ratingbunny.routes

import cats.effect.IO
import ch.qos.logback.classic.Logger as LogbackLogger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import munit.CatsEffectSuite
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.headers.Origin.Host
import org.http4s.implicits.*
import org.http4s.Uri.RegName
import org.http4s.Uri.Scheme.http
import org.slf4j.LoggerFactory
import org.typelevel.ci.CIString
import scala.jdk.CollectionConverters.*

class CORSSetupSuite extends CatsEffectSuite:

  // A simple dummy service to test against.
  private val dummyRoutes: HttpRoutes[IO] = HttpRoutes.of[IO]:
    case GET -> Root / "ping" =>
      Ok("pong")

  private val testOrigins: Set[Host] = Set(
    Host(http, RegName("localhost"), None)
  )

  // Wrap the dummy service with CORSSetup.methodConfig.
  private val corsRoutes: HttpRoutes[IO] = CORSSetup.methodConfig(dummyRoutes, testOrigins)

  private val originCI = CIString("Origin")

  test("GET request with allowed origin contains CORS headers"):
    // Pass an allowed origin; by default, CORSSetup uses env ORIGINS with "localhost".
    val req = Request[IO](Method.GET, uri"/ping").putHeaders(Header.Raw(originCI, "http://localhost"))
    for
      respOpt <- corsRoutes(req).value
      resp    <- IO.fromEither(respOpt.toRight(new Exception("Expected response")))
      // Check that the Access-Control-Allow-Origin header is present.
      allowOrigin = resp.headers.get(CIString("Access-Control-Allow-Origin")).map(_.head.value)
    yield assertEquals(allowOrigin, Some("http://localhost"))

  test("GET request with disallowed origin results in no response from service"):
    // Use an origin not allowed by the checkOrigin function.
    val req = Request[IO](Method.GET, uri"/ping").putHeaders(Header.Raw(originCI, "http://example.com"))
    corsRoutes(req).value.map:
      case Some(resp) =>
        val allowOriginOpt = resp.headers.get(CIString("Access-Control-Allow-Origin"))
        assert(allowOriginOpt.isEmpty, "Expected no Access-Control-Allow-Origin header.")
      case None =>
        fail("Expected a response, even if the CORS header is not present.")

  /** Runs parseOrigins and returns what it accepted, rendered, plus the messages it logged. */
  private def parsed(raw: String): (Set[String], List[String]) =
    val root     = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).asInstanceOf[LogbackLogger]
    val appender = ListAppender[ILoggingEvent]()
    appender.start()
    root.addAppender(appender)
    try (CORSSetup.parseOrigins(raw).map(_.renderString), appender.list.asScala.map(_.getFormattedMessage).toList)
    finally root.detachAppender(appender)

  test("parseOrigins allows an entry without a scheme over http and https, and logs nothing for the production list"):
    val (hosts, logged) = parsed("localhost:3000,localhost:8081,ratingbunny.com,saillon.iscs-i.com,192.168.15.49")
    assertEquals(
      hosts,
      Set("localhost:3000", "localhost:8081", "ratingbunny.com", "saillon.iscs-i.com", "192.168.15.49")
        .flatMap(h => Set(s"http://$h", s"https://$h"))
    )
    assertEquals(logged, Nil)

  test("parseOrigins keeps an explicit scheme, trims entries and skips blanks and a trailing slash"):
    val (hosts, logged) = parsed(" https://beta.ratingbunny.com/ ,, http://localhost:3000 ,")
    assertEquals(hosts, Set("https://beta.ratingbunny.com", "http://localhost:3000"))
    assertEquals(logged, Nil)

  test("parseOrigins logs and skips entries that are not origins"):
    val (hosts, logged) = parsed("ratingbunny.com,ratingbunny.com:notaport,*")
    assertEquals(hosts, Set("http://ratingbunny.com", "https://ratingbunny.com"))
    assertEquals(
      logged,
      List(
        "ignoring ORIGINS entry 'ratingbunny.com:notaport': not an origin",
        "ignoring ORIGINS entry '*': wildcards are not supported, list each origin"
      )
    )

  test("a scheme-less ORIGINS entry lets the https origin through CORS"):
    val routes = CORSSetup.methodConfig(dummyRoutes, CORSSetup.parseOrigins("ratingbunny.com"))
    val req    = Request[IO](Method.GET, uri"/ping").putHeaders(Header.Raw(originCI, "https://ratingbunny.com"))
    routes(req).value.map(resp =>
      assertEquals(resp.flatMap(_.headers.get(CIString("Access-Control-Allow-Origin")).map(_.head.value)), Some("https://ratingbunny.com"))
    )

  test("RouteNotFound returns RouteMessage wrapping the provided string"):
    val badValue     = "test-value"
    val routeMessage = CORSSetup.RouteNotFound(badValue)
    assertEquals(routeMessage.message, badValue)
