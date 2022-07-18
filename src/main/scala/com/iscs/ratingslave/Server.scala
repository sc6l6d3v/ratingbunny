package com.iscs.ratingslave

import java.util.concurrent.Executors

import cats.effect.{Concurrent, ConcurrentEffect, ContextShift, Timer}
import cats.implicits._
import com.iscs.ratingslave.domains.{EmailContact, ImdbQuery, ReleaseDatesScraperService}
import com.iscs.ratingslave.routes.Routes._
import com.iscs.ratingslave.util.DbClient
import com.typesafe.scalalogging.Logger
import fs2.Stream
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.{Logger => hpLogger}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}

object Server {
  private val L = Logger[this.type]

  private val port = sys.env.getOrElse("PORT", "8080").toInt
  private val bindHost = sys.env.getOrElse("BINDHOST", "0.0.0.0")
  private val serverPoolSize = sys.env.getOrElse("SERVERPOOL", "16").toInt
  private val defaultHost = sys.env.getOrElse("DATASOURCE", "www.dummy.com")

  def getPool[F[_] : Concurrent](size: Int): Stream[F, ExecutionContextExecutorService] = for {
    es <- Stream.eval(Concurrent[F].delay(Executors.newFixedThreadPool(size)))
    ex <- Stream.eval(Concurrent[F].delay(ExecutionContext.fromExecutorService(es)))
  } yield ex

  def stream[F[_]: ConcurrentEffect](mongo: DbClient[F])
                                    (implicit T: Timer[F], Con: ContextShift[F]): Stream[F, Nothing] = {
    val srvStream = for {
      imdbSvc <- Stream.eval(Concurrent[F].delay(ImdbQuery.impl[F](mongo)))
      emailSvc <- Stream.eval(Concurrent[F].delay(EmailContact.impl[F](mongo)))
      scrapeSvc <- Stream.eval(Concurrent[F].delay(new ReleaseDatesScraperService[F](defaultHost)))
      httpApp <- Stream.eval(Concurrent[F].delay(
        (
          scrapeRoutes[F](scrapeSvc) <+>
            emailContactRoutes[F](emailSvc) <+>
            imdbRoutes[F](imdbSvc)
          )
          .orNotFound))
      _ <- Stream.eval(Concurrent[F].delay(L.info(s""""added routes for reldate""")))
      finalHttpApp <- Stream.eval(Concurrent[F].delay(hpLogger.httpApp(logHeaders = true, logBody = true)(httpApp)))
      _ <- Stream.eval(Concurrent[F].delay(L.info(s""""on $bindHost/$port""")))

      serverPool <- getPool(serverPoolSize)
      exitCode <- BlazeServerBuilder[F](serverPool)
        .bindHttp(port, bindHost)
        .withHttpApp(finalHttpApp)
        .serve
    } yield exitCode
    srvStream.drain
  }
}