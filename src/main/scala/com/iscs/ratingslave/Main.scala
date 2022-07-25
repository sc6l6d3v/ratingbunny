package com.iscs.ratingslave

import cats.effect.{ExitCode, IO, IOApp, Resource}
import com.iscs.ratingslave.util.{DbClient, Mongo}
import com.typesafe.scalalogging.Logger

object Main extends IOApp {
  private val L = Logger[this.type]
  private val dbName = sys.env.getOrElse("DBNAME", "db")
  private val collNames = List("title_basics_ratings", "name_basics", "email_contact", "title_principals_withname")

  def run(args: List[String]): IO[ExitCode] = for {
    dbClient <- IO.delay(new DbClient[IO](dbName, collNames, Mongo.fromUrl()))
    resources = dbClient.dbResource
    ec <- resources
      .use { mongoClient =>
        for {
          db <- mongoClient.getDatabase(dbName)
          s <- Server.stream[IO](db)
            .compile.drain.as(ExitCode.Success)
            .handleErrorWith(ex => IO {
              L.error("\"exception during stream startup\" exception={} ex={}", ex.toString, ex)
              ExitCode.Error
            })
        } yield s
      }
  } yield ec
}