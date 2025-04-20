package com.iscs.ratingbunny.routes

import cats.effect.IO
import munit.CatsEffectSuite
import org.http4s.*
import org.http4s.client.Client
import org.http4s.implicits.*
import org.typelevel.ci.CIString
import fs2.Stream

// Create a dummy HttpApp that always returns a NotFound response.
val dummyHttpApp: HttpApp[IO] = HttpApp[IO] { _ =>
  IO.pure(Response(Status.NotFound))
}

// Create a dummy HTTP client from the dummy HttpApp.
val dummyClient: Client[IO] = Client.fromHttpApp(dummyHttpApp)

// Dummy implementation for FetchImage service.
object DummyFetchImage extends com.iscs.ratingbunny.domains.FetchImage[IO](
      defaultHost = "dummyDefaultHost",
      imageHost = "dummyImageHost",
      client = dummyClient
    ) {
  override def getImage(imdb: String): Stream[IO, Byte] =
    Stream.emits("dummyImageData".getBytes).covary[IO]
}

class FetchImageRoutesSuite extends CatsEffectSuite {

  private val fetchImageRoutes: HttpRoutes[IO] = FetchImageRoutes.httpRoutes[IO](DummyFetchImage)
  private val originCI: CIString               = CIString("Origin")

  test("GET /api/v3/image/:imdbId with allowed origin returns CORS header") {
    val imdbId = "tt1234567"
    val req = Request[IO](Method.GET, uri"/api/v3/image" / imdbId)
      .putHeaders(Header.Raw(originCI, "http://localhost"))
    fetchImageRoutes(req).value.map {
      case Some(resp) =>
        val allowOrigin = resp.headers.get(CIString("Access-Control-Allow-Origin")).map(_.head.value)
        assertEquals(allowOrigin, Some("http://localhost"), "Expected Access-Control-Allow-Origin header for allowed origin")
      case None =>
        fail("Expected a response for the allowed origin")
    }
  }

  test("GET /api/v3/image/:imdbId with disallowed origin returns response without CORS header") {
    val imdbId = "tt7654321"
    val req = Request[IO](Method.GET, uri"/api/v3/image" / imdbId)
      .putHeaders(Header.Raw(originCI, "http://example.com"))
    fetchImageRoutes(req).value.map {
      case Some(resp) =>
        val allowOriginOpt = resp.headers.get(CIString("Access-Control-Allow-Origin"))
        assert(allowOriginOpt.isEmpty, "Expected no Access-Control-Allow-Origin header for disallowed origin")
      case None =>
        fail("Expected a response even for a disallowed origin")
    }
  }
}
