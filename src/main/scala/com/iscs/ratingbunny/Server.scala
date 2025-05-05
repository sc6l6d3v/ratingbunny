package com.iscs.ratingbunny

import cats.Parallel
import cats.effect.{Async, Resource, Sync}
import cats.implicits.*
import com.comcast.ip4s.*
import com.iscs.ratingbunny.domains.{
  AuthCheck,
  AuthCheckImpl,
  ConnectionPool,
  ConnectionPoolImpl,
  EmailContact,
  EmailContactImpl,
  FetchImage,
  ImdbQuery,
  ImdbQueryImpl,
  TitleRec,
  UserDoc,
  UserProfileDoc
}
import com.iscs.ratingbunny.repos.HistoryRepo
import com.iscs.ratingbunny.routes.{AuthRoutes, EmailContactRoutes, FetchImageRoutes, ImdbRoutes, PoolSvcRoutes}
import com.iscs.ratingbunny.util.BcryptHasher
import com.typesafe.scalalogging.Logger
import io.circe.generic.auto.*
import fs2.io.net.Network
import mongo4cats.circe.*
import mongo4cats.database.MongoDatabase
import org.http4s.HttpApp
import org.http4s.client.Client
import org.http4s.ember.server.*
import org.http4s.implicits.*
import org.http4s.server.middleware.Logger as hpLogger
import org.http4s.server.{Router, Server}

object Server:
  private val L = Logger[this.type]

  private val port           = sys.env.getOrElse("PORT", "8080").toInt
  private val bindHost       = sys.env.getOrElse("BINDHOST", "0.0.0.0")
  private val serverPoolSize = sys.env.getOrElse("SERVERPOOL", "16").toInt
  private val defaultHost    = sys.env.getOrElse("DATASOURCE", "www.dummy.com")
  private val imageHost      = sys.env.getOrElse("IMAGESOURCE", "localhost:8083")

  private val compositeCollection   = "title_principals_namerating"
  private val emailCollection       = "email_contact"
  private val tbrCollection         = "title_basics_ratings"
  private val usersCollection       = "users"
  private val userProfileCollection = "user_profile"

  private def getAuthSvc[F[_]: Async](db: MongoDatabase[F]): F[AuthCheck[F]] =
    for
      userCollCodec     <- db.getCollectionWithCodec[UserDoc](usersCollection)
      userProfCollCodec <- db.getCollectionWithCodec[UserProfileDoc](userProfileCollection)
    yield
      val hasher = BcryptHasher.make[F](cost = 12)
      new AuthCheckImpl(userCollCodec, userProfCollCodec, hasher)

  private def getImdbSvc[F[_]: Async: Parallel](db: MongoDatabase[F], client: Client[F]): F[ImdbQuery[F]] =
    for
      compCollCodec <- db.getCollectionWithCodec[TitleRec](compositeCollection)
      tbrCollCodec  <- db.getCollectionWithCodec[TitleRec](tbrCollection)
    yield new ImdbQueryImpl[F](compCollCodec, tbrCollCodec, imageHost, Some(client))

  private def getPoolStatsSvc[F[_]: Async: Parallel](db: MongoDatabase[F]): F[ConnectionPool[F]] =
    Sync[F].delay(new ConnectionPoolImpl[F](db))

  private def getEmailSvc[F[_]: Async](db: MongoDatabase[F]): F[EmailContact[F]] =
    for emailColl <- db.getCollection(emailCollection)
    yield new EmailContactImpl[F](emailColl)

  def getServices[F[_]: Async: Parallel](db: MongoDatabase[F], client: Client[F]): F[HttpApp[F]] =
    for
      authSvc     <- getAuthSvc(db)
      emailSvc    <- getEmailSvc(db)
      fetchSvc    <- Sync[F].delay(new FetchImage[F](defaultHost, imageHost, client))
      historyRepo <- HistoryRepo.make(db)
      imdbSvc     <- getImdbSvc(db, client)
      poolSvc     <- getPoolStatsSvc(db)
      httpApp <- Sync[F].delay(
        Router(
          "/" ->
            (FetchImageRoutes.httpRoutes(fetchSvc) <+>
              EmailContactRoutes.httpRoutes(emailSvc) <+>
              ImdbRoutes.httpRoutes(imdbSvc, historyRepo) <+>
              PoolSvcRoutes.httpRoutes(poolSvc) <+>
              AuthRoutes.httpRoutes(authSvc))
        ).orNotFound
      )
      _            <- Sync[F].delay(L.info(s""""added routes for auth, email, hx, imdb, pool, """))
      finalHttpApp <- Sync[F].delay(hpLogger.httpApp(logHeaders = true, logBody = false)(httpApp))
    yield finalHttpApp

  def getResource[F[_]: Async](finalHttpApp: HttpApp[F]): Resource[F, Server] =
    implicit val networkInstance: Network[F] = Network.forAsync[F]
    for server <- EmberServerBuilder
        .default[F]
        .withHost(Ipv4Address.fromString(bindHost).getOrElse(ipv4"0.0.0.0"))
        .withPort(Port.fromInt(port).getOrElse(port"8080"))
        .withHttpApp(finalHttpApp)
        .withMaxConnections(serverPoolSize)
        .build
    yield server
