package com.iscs.ratingslave

import cats.effect.{ExitCode, IO, IOApp}
import com.iscs.ratingslave.util.{DbClient, Mongo}
import com.typesafe.scalalogging.Logger

object Main extends IOApp {
  private val L = Logger[this.type]
  private val dbName = sys.env.getOrElse("DBNAME", "db")

  def run(args: List[String]): IO[ExitCode] = for {
    dbClient <- IO.delay(new DbClient[IO](Mongo.fromUrl()))
    resource <- IO.delay(dbClient.dbResource)
    ec <- resource.use { mongoClient =>
      for {
        db <- mongoClient.getDatabase(dbName)
        services <- Server.getServices(db)
        serverResource <- IO.delay(Server.getResource(services))
        ec2 <- serverResource.use { _ => IO.never }
          .as(ExitCode.Success)
          .handleErrorWith(ex => IO {
            L.error("\"exception during stream startup\" exception={} ex={}", ex.toString, ex)
            ExitCode.Error
          })
      } yield ec2
    }
  } yield ec
}