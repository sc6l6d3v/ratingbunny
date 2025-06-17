package com.iscs.ratingbunny

import cats.effect.{ExitCode, IO, IOApp}
import com.iscs.ratingbunny.config.RedisConfig
import com.iscs.ratingbunny.util.DbClient
import com.typesafe.scalalogging.Logger
import dev.profunktor.redis4cats.effect.Log.Stdout.*
import org.http4s.ember.client.EmberClientBuilder

object Main extends IOApp {
  private val L      = Logger[this.type]
  private val dbName = sys.env.getOrElse("DBNAME", "db")

  def run(args: List[String]): IO[ExitCode] = for {
    dbClient <- IO.delay(new DbClient[IO](DbClient.fromUrl("MONGOURI")))
    resources <- IO.delay(for {
      redis <- new RedisConfig[IO]().resource
      dbres <- dbClient.dbResource
      clres <- EmberClientBuilder.default[IO].build
    } yield (redis, dbres, clres))
    ec <- resources.use { case (redis, dbClient, emberClient) =>
      for {
        db             <- dbClient.getDatabase(dbName)
        services       <- Server.getServices(redis, db, emberClient)
        serverResource <- IO.delay(Server.getResource(services))
        ec2 <- serverResource
          .use(_ => IO.never)
          .as(ExitCode.Success)
          .handleErrorWith(ex =>
            IO {
              L.error("\"exception during stream startup\" exception={} ex={}", ex.toString, ex)
              ExitCode.Error
            }
          )
      } yield ec2
    }
  } yield ec
}
