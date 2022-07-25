package com.iscs.ratingslave

import java.util.concurrent.Executors

import cats.effect.{Async, Sync}
import cats.implicits._
import com.iscs.ratingslave.domains.{EmailContact, ImdbQuery, ReleaseDates}
import com.iscs.ratingslave.routes.Routes._
import com.typesafe.scalalogging.Logger
import fs2.Stream
import mongo4cats.database.MongoDatabase
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

  private val nameCollection = "name_basics"
  private val titleCollection = "title_basics_ratings"
  private val titlePrincipalsCollection = "title_principals_withname"

  private val emailCollection = "email_contact"

  def getPool[F[_] : Sync](size: Int): Stream[F, ExecutionContextExecutorService] = for {
    es <- Stream.eval(Sync[F].delay(Executors.newFixedThreadPool(size)))
    ex <- Stream.eval(Sync[F].delay(ExecutionContext.fromExecutorService(es)))
  } yield ex

  def stream[F[_]: Async](db: MongoDatabase[F]): Stream[F, Nothing] = {
    val srvStream = for {
      titleColl <- Stream.eval(db.getCollection(titleCollection))
      titlePrincipleColl <- Stream.eval(db.getCollection(titlePrincipalsCollection))
      nameColl <- Stream.eval(db.getCollection(nameCollection))
      imdbSvc <- Stream.eval(Sync[F].delay(ImdbQuery.impl[F](titleColl, titlePrincipleColl, nameColl)))
      emailColl <- Stream.eval(db.getCollection(emailCollection))
      emailSvc <- Stream.eval(Sync[F].delay(EmailContact.impl[F](emailColl)))
      scrapeSvc <- Stream.eval(Sync[F].delay(new ReleaseDates[F](defaultHost)))
      httpApp <- Stream.eval(Sync[F].delay(
        (
          scrapeRoutes[F](scrapeSvc) <+>
            emailContactRoutes[F](emailSvc) <+>
            imdbRoutes[F](imdbSvc)
          )
          .orNotFound))
      _ <- Stream.eval(Sync[F].delay(L.info(s""""added routes for reldate""")))
      finalHttpApp <- Stream.eval(Sync[F].delay(hpLogger.httpApp(logHeaders = true, logBody = true)(httpApp)))
      _ <- Stream.eval(Sync[F].delay(L.info(s""""on $bindHost/$port""")))

      serverPool <- getPool(serverPoolSize)
      exitCode <- BlazeServerBuilder[F](serverPool)
        .bindHttp(port, bindHost)
        .withHttpApp(finalHttpApp)
        .serve
    } yield exitCode
    srvStream.drain
  }
}