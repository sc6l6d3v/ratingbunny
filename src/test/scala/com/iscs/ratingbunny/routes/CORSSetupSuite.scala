package com.iscs.ratingbunny.routes

import cats.effect.IO
import munit.CatsEffectSuite
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.implicits.*
import org.typelevel.ci.CIString

class CORSSetupSuite extends CatsEffectSuite:

  // A simple dummy service to test against.
  private val dummyRoutes: HttpRoutes[IO] = HttpRoutes.of[IO]:
    case GET -> Root / "ping" =>
      Ok("pong")

  // Wrap the dummy service with CORSSetup.methodConfig.
  private val corsRoutes: HttpRoutes[IO] = CORSSetup.methodConfig(dummyRoutes)

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

  test("RouteNotFound returns RouteMessage wrapping the provided string"):
    val badValue     = "test-value"
    val routeMessage = CORSSetup.RouteNotFound(badValue)
    assertEquals(routeMessage.message, badValue)
