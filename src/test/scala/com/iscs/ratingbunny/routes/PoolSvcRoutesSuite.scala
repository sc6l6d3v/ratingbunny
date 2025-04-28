package com.iscs.ratingbunny.routes

import cats.effect.IO
import munit.CatsEffectSuite
import org.http4s.*
import org.http4s.implicits.*
import org.http4s.circe.CirceEntityCodec.*
import org.typelevel.ci.CIString
import io.circe.Json

// A dummy implementation of the ConnectionPool domain
object DummyConnectionPool extends com.iscs.ratingbunny.domains.ConnectionPool[IO] {
  override def getCPStats: IO[Json] = IO.pure(Json.fromString("dummyPoolStats"))
}

class PoolSvcRoutesSuite extends CatsEffectSuite {

  private val poolSvcRoutes: HttpRoutes[IO] = PoolSvcRoutes.httpRoutes[IO](DummyConnectionPool)
  private val originHeader: CIString        = CIString("Origin")

  test("GET /api/v3/poolStats returns dummy pool stats") {
    val req = Request[IO](Method.GET, uri"/api/v3/poolStats")
    poolSvcRoutes(req).value.flatMap {
      case Some(resp) =>
        // Check the status and body
        resp.as[String].map { body =>
          assertEquals(resp.status, Status.Ok)
          assertEquals(body, """dummyPoolStats""" /* expected JSON string literal */ )
        }
      case None =>
        IO(fail("Expected a response from the route"))
    }
  }

  test("GET /api/v3/poolStats with disallowed origin should not set CORS header") {
    val req = Request[IO](Method.GET, uri"/api/v3/poolStats")
      .putHeaders(Header.Raw(originHeader, "http://example.com"))
    poolSvcRoutes(req).value.flatMap {
      case Some(resp) =>
        // Assert that Access-Control-Allow-Origin header is not set for a disallowed origin.
        IO(assert(resp.headers.get(CIString("Access-Control-Allow-Origin")).isEmpty, "Expected no CORS header"))
      case None =>
        IO(fail("Expected a response from the route"))
    }
  }
}
