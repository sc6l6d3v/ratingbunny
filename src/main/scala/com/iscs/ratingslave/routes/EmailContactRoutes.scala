package com.iscs.ratingslave.routes

import cats.effect._
import cats.implicits._
import com.iscs.ratingslave.domains.EmailContact
import com.iscs.ratingslave.domains.EmailContact.Email
import io.circe.generic.auto._
import org.http4s.circe.CirceEntityCodec.{circeEntityDecoder, circeEntityEncoder}
import com.typesafe.scalalogging.Logger
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl

object EmailContactRoutes {
  private val L = Logger[this.type]
  private val apiVersion = "v3"

  def httpRoutes[F[_]: Async](E: EmailContact[F]): HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl._
    val svc = HttpRoutes.of[F] {
      case req@POST -> Root / "api" / `apiVersion` / "addMsg" =>
        for {
          email <- req.as[Email]
          _ <- Sync[F].delay(L.info(s""""request" ${email.name} ${email.email} ${email.subject} ${email.msg}"""))
          emailId <- E.saveEmail(email.name, email.email, email.subject, email.msg)
          resp <- Ok(emailId)
        } yield resp
    }
    CORSSetup.methodConfig(svc)
  }
}
