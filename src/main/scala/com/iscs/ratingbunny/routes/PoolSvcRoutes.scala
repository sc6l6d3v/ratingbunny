package com.iscs.ratingbunny.routes

import cats.effect._
import cats.implicits._
import com.iscs.ratingbunny.domains.ConnectionPool
import com.typesafe.scalalogging.Logger
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityCodec.circeEntityEncoder
import org.http4s.dsl.Http4sDsl

object PoolSvcRoutes {
  private val L          = Logger[this.type]
  private val apiVersion = "v3"

  def httpRoutes[F[_]: Async](E: ConnectionPool[F]): HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl._
    val svc = HttpRoutes.of[F] { case GET -> Root / "api" / `apiVersion` / "poolStats" =>
      for {
        poolStats <- E.getCPStats
        _         <- Sync[F].delay(L.info(s""""request poolStats" $poolStats"""))
        resp      <- Ok(poolStats)
      } yield resp
    }
    CORSSetup.methodConfig(svc)
  }
}
