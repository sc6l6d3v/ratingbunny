package com.iscs.releaseScraper

import cats.effect.{Concurrent, ExitCode, IO, IOApp, Resource}
import cats.implicits._
import com.iscs.releaseScraper.util.{DbClient, Mongo}
import com.typesafe.scalalogging.Logger

object Main extends IOApp {
  private val L = Logger[this.type]
  private val dbName = sys.env.getOrElse("DBNAME", "db")

  def run(args: List[String]): IO[ExitCode] = for {
    _ <- IO.delay(System.currentTimeMillis)
    resources = for {
      mongoClient <- Resource.fromAutoCloseable(Concurrent[IO].delay(
        DbClient[IO](Mongo.fromUrl(), dbName, List("title_basics_ratings", "name_basics"))))
    } yield (IO.delay(mongoClient))

    ec <- resources.use { dbClient =>
      for {
        start <- IO.delay(System.currentTimeMillis)
        serverStream = for {
          str <- Server.stream[IO](dbClient)
        } yield str

        s <- serverStream
          .compile.drain.as(ExitCode.Success)
          .handleErrorWith(ex => IO {
            L.error("\"exception during stream startup\" exception={} ex={}", ex.toString, ex)
            ExitCode.Error
          })
      } yield s
    }
  } yield ec
}