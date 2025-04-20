package com.iscs.ratingbunny.routes

import cats.effect.IO
import munit.CatsEffectSuite
import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.io.*
import org.http4s.implicits.*
import org.typelevel.ci.CIString
import io.circe.generic.auto.*
import io.circe.syntax.*
import com.iscs.ratingbunny.domains.{Email, EmailContact}

object DummyEmailContact extends EmailContact[IO] {
  def saveEmail(name: String, email: String, subject: String, msg: String): IO[String] = IO.pure("dummyId")
}

class EmailContactRoutesSuite extends CatsEffectSuite {

  // Create the EmailContactRoutes service with the dummy implementation.
  private val emailRoutes: HttpRoutes[IO] = EmailContactRoutes.httpRoutes[IO](DummyEmailContact)
  private val originCI: CIString          = CIString("Origin")

  test("POST /api/v3/addMsg with disallowed origin returns response without CORS header") {
    // Create a sample email payload.
    val sampleEmail = Email("John Doe", "john.doe@example.com", "Greetings", "Hello there!")
    // Build the request with a disallowed origin.
    val req = Request[IO](Method.POST, uri"/api/v3/addMsg")
      .withEntity(sampleEmail.asJson)
      .putHeaders(Header.Raw(originCI, "http://example.com"))

    emailRoutes(req).value.map {
      case Some(resp) =>
        val allowOriginOpt = resp.headers.get(CIString("Access-Control-Allow-Origin"))
        assert(
          allowOriginOpt.isEmpty,
          "Expected no Access-Control-Allow-Origin header for disallowed origin"
        )
      case None =>
        fail("Expected a response even with a disallowed origin")
    }
  }
}
