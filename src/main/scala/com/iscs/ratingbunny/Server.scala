package com.iscs.ratingbunny

import cats.Parallel
import cats.effect.{Async, Resource, Sync}
import cats.implicits.*
import com.comcast.ip4s.*
import com.iscs.mail.{EmailService, EmailServiceConfig}
import com.iscs.ratingbunny.config.TrialConfig
import com.iscs.ratingbunny.domains.{
  AuthCheck,
  AuthCheckImpl,
  AuthLogin,
  AuthLoginImpl,
  AutoNameRec,
  AutoTitleRec,
  BillingInfo,
  BillingWorkflow,
  ConnectionPool,
  ConnectionPoolImpl,
  CountryAwareBillingWorkflow,
  EmailContact,
  EmailContactImpl,
  FetchImage,
  ImdbQuery,
  ImdbQueryImpl,
  PasswordResetService,
  PasswordResetServiceImpl,
  PasswordResetTokenDoc,
  TitleRec,
  TokenIssuer,
  TokenIssuerImpl,
  TrialService,
  TrialServiceImpl,
  UserDoc,
  UserProfileDoc,
  UserRepo,
  UserRepoImpl
}
import com.iscs.ratingbunny.repos.HistoryRepo
import com.iscs.ratingbunny.routes.{
  AuthRoutes,
  EmailContactRoutes,
  FetchImageRoutes,
  HelcimRoutes,
  HistoryRoutes,
  ImdbRoutes,
  PoolSvcRoutes
}
import com.iscs.ratingbunny.security.JwtAuth
import com.iscs.ratingbunny.util.BcryptHasher
import com.typesafe.scalalogging.Logger
import dev.profunktor.redis4cats.RedisCommands
import fs2.io.file.Files
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
  private val imageHost      = sys.env.getOrElse("IMAGESOURCE", "localhost:8083")
  private val ServiceConfig = EmailServiceConfig(
    smtpUrl = sys.env.getOrElse("SMTPURL", "smtp://localhost:25"),
    fromAddress = sys.env.getOrElse("FROMADDR", "contact@example.com"),
    username = sys.env.getOrElse("USERID", "contact@example.com"),
    password = sys.env.getOrElse("EMAILPWD", "dummy123"),
    debug = sys.env.getOrElse("DEBUG", "false").toBoolean
  )

  private val peopleCollection        = "people"
  private val peopleTitlesCollection  = "people_titles"
  private val emailCollection         = "email_contact"
  private val tbrCollection           = "titles"
  private val usersCollection         = "users"
  private val userProfileCollection   = "user_profile"
  private val billingCollection       = "billing_info"
  private val passwordResetCollection = "password_reset_tokens"
  private val apiVersion              = "v3"
  private val helcimApiToken =
    sys.env.getOrElse("HELCIM_API_TOKEN", throw new RuntimeException("HELCIM_API_TOKEN environment variable must be set"))

  private val jwtSecretKey =
    sys.env.getOrElse("JWT_SECRET_KEY", throw new RuntimeException("JWT_SECRET_KEY environment variable must be set"))
  private val resetHost = sys.env.getOrElse("RESET_HOST", "https://example.com")

  private val trialConfig = TrialConfig.fromEnv()

  private def getAuthSvc[F[_]: Async](
      db: MongoDatabase[F],
      emailService: EmailService[F],
      billingWorkflow: BillingWorkflow[F],
      trialConfig: TrialConfig
  ): F[AuthCheck[F]] =
    for
      userCollCodec     <- db.getCollectionWithCodec[UserDoc](usersCollection)
      userProfCollCodec <- db.getCollectionWithCodec[UserProfileDoc](userProfileCollection)
      billingCollCodec  <- db.getCollectionWithCodec[BillingInfo](billingCollection)
    yield
      val hasher = BcryptHasher.make[F](cost = 12)
      new AuthCheckImpl(userCollCodec, userProfCollCodec, billingCollCodec, hasher, emailService, billingWorkflow, trialConfig)

  private def getLoginSvc[F[_]: Async](db: MongoDatabase[F], token: TokenIssuer[F]): F[AuthLogin[F]] =
    for userCollCodec <- db.getCollectionWithCodec[UserDoc](usersCollection)
    yield
      val hasher = BcryptHasher.make[F](cost = 12)
      new AuthLoginImpl(userCollCodec, hasher, token)

  private def getUserRepoSvc[F[_]: Async](db: MongoDatabase[F]): F[UserRepo[F]] =
    for
      userCollCodec <- db.getCollectionWithCodec[UserDoc](usersCollection)
      repo          <- UserRepoImpl.make[F](userCollCodec)
    yield repo

  private def getTrialSvc[F[_]: Async](
      db: MongoDatabase[F],
      userRepo: UserRepo[F],
      billingWorkflow: BillingWorkflow[F]
  ): F[TrialService[F]] =
    for billingCol <- db.getCollectionWithCodec[BillingInfo](billingCollection)
    yield TrialServiceImpl.make(userRepo, billingCol, billingWorkflow)

  private def getPasswordResetSvc[F[_]: Async](
      db: MongoDatabase[F],
      tokenIssuer: TokenIssuer[F],
      emailService: EmailService[F]
  ): F[PasswordResetService[F]] =
    for
      usersCol  <- db.getCollectionWithCodec[UserDoc](usersCollection)
      tokenColl <- db.getCollectionWithCodec[PasswordResetTokenDoc](passwordResetCollection)
      hasher = BcryptHasher.make[F](cost = 12)
    yield PasswordResetServiceImpl.make(usersCol, tokenColl, hasher, emailService, tokenIssuer, resetHost)

  private def getTokenIssuerSvc[F[_]: Async](
      redis: RedisCommands[F, String, String],
      db: MongoDatabase[F],
      key: String
  ): F[TokenIssuer[F]] =
    for userRepo <- getUserRepoSvc(db)
    yield new TokenIssuerImpl[F](redis, userRepo, key)

  private def getImdbSvc[F[_]: Async: Parallel](db: MongoDatabase[F], client: Client[F]): F[ImdbQuery[F]] =
    for
      peopleCollCodec       <- db.getCollectionWithCodec[AutoNameRec](peopleCollection)
      peopleTitlesCollCodec <- db.getCollectionWithCodec[TitleRec](peopleTitlesCollection)
      titlesCollCodec       <- db.getCollectionWithCodec[TitleRec](tbrCollection)
      autoTitlesCollCodec   <- db.getCollectionWithCodec[AutoTitleRec](tbrCollection)
    yield new ImdbQueryImpl[F](peopleCollCodec, peopleTitlesCollCodec, titlesCollCodec, autoTitlesCollCodec, imageHost, Some(client))

  private def getPoolStatsSvc[F[_]: Async: Parallel](db: MongoDatabase[F]): F[ConnectionPool[F]] =
    Sync[F].delay(new ConnectionPoolImpl[F](db))

  private def getEmailSvc[F[_]: Async: Files](db: MongoDatabase[F], emailService: EmailService[F]): F[EmailContact[F]] =
    for emailColl <- db.getCollection(emailCollection)
    yield new EmailContactImpl[F](emailColl, emailService)

  def getServices[F[_]: Async: Files: Parallel](
      redis: RedisCommands[F, String, String],
      db: MongoDatabase[F],
      client: Client[F]
  ): F[HttpApp[F]] =
    given Network[F] = Network.forAsync[F]
    for
      token           <- getTokenIssuerSvc(redis, db, jwtSecretKey)
      emailService    <- EmailService.initialize(ServiceConfig)
      billingWorkflow <- CountryAwareBillingWorkflow.make[F](trialConfig)
      authSvc         <- getAuthSvc(db, emailService, billingWorkflow, trialConfig)
      loginSvc        <- getLoginSvc(db, token)
      emailSvc        <- getEmailSvc(db, emailService)
      userRepo        <- getUserRepoSvc(db)
      passwordReset   <- getPasswordResetSvc(db, token, emailService)
      trialSvc        <- getTrialSvc(db, userRepo, billingWorkflow)
      fetchSvc        <- Sync[F].delay(new FetchImage[F](imageHost, client))
      historyRepo     <- HistoryRepo.make(db)
      imdbSvc         <- getImdbSvc(db, client)
      poolSvc         <- getPoolStatsSvc(db)
      authMw = JwtAuth.middleware(jwtSecretKey)
      httpApp = Router(
        s"/api/$apiVersion" ->
          (FetchImageRoutes.httpRoutes(fetchSvc) <+>
            HelcimRoutes.httpRoutes(client, helcimApiToken, redis) <+>
            EmailContactRoutes.httpRoutes(emailSvc) <+>
            ImdbRoutes.publicRoutes(imdbSvc, historyRepo, jwtSecretKey) <+>
            PoolSvcRoutes.httpRoutes(poolSvc) <+>
            AuthRoutes.httpRoutes(authSvc, loginSvc, userRepo, token, passwordReset) <+>
            AuthRoutes.authedRoutes(userRepo, trialSvc, authMw)),
        s"/api/$apiVersion/pro" ->
          (ImdbRoutes.authedRoutes(imdbSvc, historyRepo, userRepo, authMw, trialConfig) <+>
            HistoryRoutes.authedRoutes(historyRepo, userRepo, trialConfig, authMw))
      ).orNotFound
      _            <- Sync[F].delay(L.info(s""""added routes for auth, email, hx, imdb, pool, """))
      finalHttpApp <- Sync[F].delay(hpLogger.httpApp(logHeaders = true, logBody = false)(httpApp))
    yield finalHttpApp

  def getResource[F[_]: Async](finalHttpApp: HttpApp[F]): Resource[F, Server] =
    given Network[F] = Network.forAsync[F]
    for server <- EmberServerBuilder
        .default[F]
        .withHost(Ipv4Address.fromString(bindHost).getOrElse(ipv4"0.0.0.0"))
        .withPort(Port.fromInt(port).getOrElse(port"8080"))
        .withHttpApp(finalHttpApp)
        .withMaxConnections(serverPoolSize)
        .build
    yield server
