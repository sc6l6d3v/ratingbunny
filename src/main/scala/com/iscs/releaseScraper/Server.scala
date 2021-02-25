package com.iscs.releaseScraper

import cats.effect.{Concurrent, ConcurrentEffect, ContextShift, Timer}
import com.iscs.releaseScraper.domains.ReleaseDatesScraperService
import com.iscs.releaseScraper.routes.Routes._
import com.typesafe.scalalogging.Logger
import fs2.Stream
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.{Logger => hpLogger}

object Server {
  private val L = Logger[this.type]

  def stream[F[_]: ConcurrentEffect](port: Int, listener: String)
                                    (implicit T: Timer[F], Con: ContextShift[F]): Stream[F, Nothing] = {
    val srvStream = for {
      scrapeSvc <- Stream.eval(Concurrent[F].delay(new ReleaseDatesScraperService[F]()))
      httpApp <- Stream.eval(Concurrent[F].delay(scrapeRoutes[F](scrapeSvc).orNotFound))
      _ <- Stream.eval(Concurrent[F].delay(L.info(s""""added routes for reldate""")))
      finalHttpApp <- Stream.eval(Concurrent[F].delay(hpLogger.httpApp(logHeaders = true, logBody = true)(httpApp)))
      _ <- Stream.eval(Concurrent[F].delay(L.info(s""""on $listener/$port""")))

      exitCode <- BlazeServerBuilder[F]
        .bindHttp(port, listener)
        .withHttpApp(finalHttpApp)
        .serve
    } yield exitCode
    srvStream.drain
  }
}