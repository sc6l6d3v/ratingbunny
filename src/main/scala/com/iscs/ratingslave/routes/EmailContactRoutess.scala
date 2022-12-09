package com.iscs.ratingslave.routes

import cats.effect.Sync
import cats.implicits._
import com.iscs.ratingslave.domains.EmailContact
import com.iscs.ratingslave.domains.EmailContact.Email
import com.iscs.ratingslave.domains.EmailContact.Email._
import com.typesafe.scalalogging.Logger
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import zio.json._

object EmailContactRoutess {
  private val L = Logger[this.type]

  private[routes] val prefixPath = "/"

  def httpRoutes[F[_]: Sync](E: EmailContact[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._
    HttpRoutes.of[F] {
      case req@POST -> Root / "api" / "v1" / "addMsg" =>
        Ok(for {
          asBytes <- req.body.compile.toList
          asJson  <- Sync[F].delay(asBytes.map(_.toChar).mkString)
          maybeEmail <- Sync[F].delay(asJson.fromJson[Option[Email]].getOrElse(Option.empty[Email]))
          emailId <- maybeEmail match {
            case Some(Email(name, email, subject, msg)) =>
              Sync[F].delay(L.info(s""""request" $name $email $subject $msg"""))
              E.saveEmail2(name, email, subject, msg)
            case _ =>
              Sync[F].delay("bad email string")
          }
        } yield emailId)
    }
  }
}
