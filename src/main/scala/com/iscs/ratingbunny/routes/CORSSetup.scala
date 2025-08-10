package com.iscs.ratingbunny.routes

import cats.effect.Async
import com.iscs.ratingbunny.model.RouteMessage.RouteMessage
import com.typesafe.scalalogging.Logger
import org.http4s.*
import org.http4s.headers.Origin
import org.http4s.headers.Origin.Null
import org.http4s.server.middleware.*
import org.typelevel.ci.*
import scala.concurrent.duration.*

object CORSSetup {
  private val L = Logger[this.type]
  private val reactDeploys: Set[Origin.Host] = sys.env
    .getOrElse("ORIGINS", "http://localhost")
    .split(",")
    .flatMap(o =>
      Origin.parse(o) match {
        case Right(hList @ Origin.HostList(_)) => hList.hosts.toList
        case Right(Null)                       => L.error("empty host"); List.empty[Origin.Host]
        case Left(l)                           => L.error(s"got bad host: $l"); List.empty[Origin.Host]
      }
    )
    .toSet
  L.info(s"got origins: ${reactDeploys.mkString(",")}")
  private val methods = Set(Method.GET, Method.POST)
  private val checkOrigin = (host: Origin.Host) => {
    val ok = reactDeploys.exists { curHosts =>
      val res = curHosts == host
      L.debug(s"compare ${curHosts.renderString} to ${host.renderString}: $res")
      res
    }
    L.debug(s"origin ${host.renderString} accepted? $ok")
    ok
  }

  def methodConfig[F[_]: Async](svc: HttpRoutes[F]): HttpRoutes[F] = CORS.policy
    .withAllowCredentials(true)
    .withMaxAge(1.day)
    .withAllowMethodsIn(methods)
    .withAllowOriginHost(checkOrigin)
    .withExposeHeadersIn(Set(ci"X-Remaining-Count"))
    .apply(svc)

  def RouteNotFound(badVal: String): RouteMessage =
    RouteMessage(badVal)
}
