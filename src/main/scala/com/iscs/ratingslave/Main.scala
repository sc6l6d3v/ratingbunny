package com.iscs.ratingslave

import cats.effect.{Concurrent, ExitCode, IO, IOApp, Resource}
import cats.implicits._
import com.iscs.ratingslave.util.{DbClient, Mongo}
import com.typesafe.scalalogging.Logger

object Main extends IOApp {
  private val L = Logger[this.type]
  private val dbName = sys.env.getOrElse("DBNAME", "db")
  private val collNames = List("title_basics_ratings", "name_basics", "email_contact", "title_principals_withname")

  def run(args: List[String]): IO[ExitCode] = for {
    resources <- IO.delay(Resource.fromAutoCloseable(Concurrent[IO].delay(DbClient[IO](Mongo.fromUrl(), dbName, collNames))))
    ec <- resources.use { dbClient =>
      for {
        s <- Server.stream[IO](dbClient)
          .compile.drain.as(ExitCode.Success)
          .handleErrorWith(ex => IO {
            L.error("\"exception during stream startup\" exception={} ex={}", ex.toString, ex)
            ExitCode.Error
          })
      } yield s
    }
  } yield ec
}