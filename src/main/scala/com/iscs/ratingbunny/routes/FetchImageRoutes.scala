package com.iscs.ratingbunny.routes

import cats.effect.{Async, Sync}
import cats.implicits.*
import com.iscs.ratingbunny.domains.FetchImage
import com.typesafe.scalalogging.Logger
import org.http4s.*
import org.http4s.CacheDirective
import org.http4s.MediaType.image.*
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.{`Cache-Control`, `Content-Type`}

import scala.concurrent.duration.*
import scala.language.postfixOps

object FetchImageRoutes:
  private val L          = Logger[this.type]

  def httpRoutes[F[_]: Async](R: FetchImage[F]): HttpRoutes[F] =
    val dsl = Http4sDsl[F]
    import dsl.*
    val imgSvc = HttpRoutes
      .of[F]:
        case _ @GET -> Root / "image" / imdbId =>
          for
            _ <- Sync[F].delay(L.info(s""""request" image=$imdbId"""))
            resp <- Ok(R.getImage(imdbId))
              .map(
                _.putHeaders(
                  `Content-Type`(MediaType.image.jpeg),
                  `Cache-Control`(
                    CacheDirective.`public`,
                    CacheDirective.`max-age`(86400 seconds) // 1 day in seconds
                  )
                )
              )
          yield resp
      .map(_.withContentType(`Content-Type`(`jpeg`)))
    CORSSetup.methodConfig(imgSvc)
