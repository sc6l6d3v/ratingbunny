package com.iscs.ratingslave

import cats.effect.{ExitCode, IO, IOApp}
import com.iscs.ratingslave.util.DbClient
import com.typesafe.scalalogging.Logger
import org.http4s.ember.client.EmberClientBuilder

object Main extends IOApp {
  private val L      = Logger[this.type]
  private val dbName = sys.env.getOrElse("DBNAME", "db")

  def run(args: List[String]): IO[ExitCode] = for {
    dbClient <- IO.delay(new DbClient[IO](DbClient.fromUrl("MONGOURI")))
    resources <- IO.delay(for {
      dbres <- dbClient.dbResource
      clres <- EmberClientBuilder.default[IO].build
    } yield (dbres, clres))
    ec <- resources.use { case (dbClient, emberClient) =>
      for {
        db             <- dbClient.getDatabase(dbName)
        services       <- Server.getServices(db, emberClient)
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
