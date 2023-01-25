package com.iscs.ratingslave.routes

import cats.effect.Async
import com.iscs.ratingslave.model.RouteMessage.RouteMessage
import com.typesafe.scalalogging.Logger
import org.http4s._
import org.http4s.headers.Origin
import org.http4s.headers.Origin.Null
import org.http4s.server.middleware._

import scala.concurrent.duration._

object CORSSetup {
  private val L = Logger[this.type]
  private val protos = List("http", "https")
  private val reactDeploys: Set[Origin.Host] = sys.env.getOrElse("ORIGINS", "localhost")
    .split(",")
    .flatMap(host => protos.map(proto => Origin.parse(s"$proto://$host")))
    .flatMap(p => p match {
      case Right(hList@Origin.HostList(_)) => hList.hosts.toList
      case Right(Null) => L.error("empty host"); List.empty[Origin.Host]
      case Left(l) => L.error(s"got bad host: $l"); List.empty[Origin.Host]
    })
    .toSet
  private val methods = Set(Method.GET, Method.POST)

  def methodConfig[F[_] : Async](svc: HttpRoutes[F]) = CORS.policy
    .withAllowCredentials(true)
    .withMaxAge(1.day)
    .withAllowMethodsIn(methods)
    .withAllowOriginHost(reactDeploys)
    .apply(svc)

  def RouteNotFound(badVal: String): RouteMessage =
    RouteMessage(badVal)
}
