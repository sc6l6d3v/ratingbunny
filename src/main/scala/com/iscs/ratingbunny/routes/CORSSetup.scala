package com.iscs.ratingbunny.routes

import cats.effect.Async
import com.iscs.ratingbunny.model.RouteMessage.RouteMessage
import com.typesafe.scalalogging.Logger
import org.http4s.*
import org.http4s.headers.Origin
import org.http4s.server.middleware.*
import org.typelevel.ci.*
import scala.concurrent.duration.*

object CORSSetup:
  private val L = Logger[this.type]

  /** Parses a comma-separated ORIGINS list. An entry with a scheme ("https://ratingbunny.com") allows only that origin; one without
    * ("ratingbunny.com", "localhost:3000") allows the host over both http and https, since a browser always sends the scheme. Blank
    * entries are skipped; wildcards (not allowed with credentialed CORS) and anything else that is not an origin are logged and skipped.
    */
  def parseOrigins(raw: String): Set[Origin.Host] =
    raw
      .split(",")
      .toList
      .map(_.trim.stripSuffix("/"))
      .filter(_.nonEmpty)
      .flatMap(entry =>
        if entry.contains("*") then
          L.error(s"ignoring ORIGINS entry '$entry': wildcards are not supported, list each origin")
          Nil
        else
          val candidates = if entry.contains("://") then List(entry) else List(s"http://$entry", s"https://$entry")
          val hosts = candidates.map(Origin.parse).flatMap:
            case Right(hList @ Origin.HostList(_)) => hList.hosts.toList
            case _                                 => Nil
          if hosts.isEmpty then L.error(s"ignoring ORIGINS entry '$entry': not an origin")
          hosts
      )
      .toSet

  val defaultOrigins: Set[Origin.Host] = parseOrigins(sys.env.getOrElse("ORIGINS", "http://localhost"))
  L.info(s"got origins: ${defaultOrigins.map(_.renderString).toList.sorted.mkString(",")}")
  private val methods = Set(Method.GET, Method.POST)

  def methodConfig[F[_]: Async](svc: HttpRoutes[F]): HttpRoutes[F] =
    methodConfig(svc, defaultOrigins)

  def methodConfig[F[_]: Async](svc: HttpRoutes[F], origins: Set[Origin.Host]): HttpRoutes[F] =
    val checkOrigin: Origin.Host => Boolean = host =>
      val ok = origins.contains(host)
      if !ok then L.debug(s"origin ${host.renderString} NOT accepted (not in ${origins.size} allowed origins)")
      else L.debug(s"origin ${host.renderString} accepted")
      ok

    CORS.policy
      .withAllowCredentials(true)
      .withMaxAge(1.day)
      .withAllowMethodsIn(methods)
      .withAllowHeadersIn(Set(ci"Content-Type", ci"Authorization", ci"Accept"))
      .withAllowOriginHost(checkOrigin)
      .withExposeHeadersIn(Set(ci"X-Remaining-Count"))
      .apply(svc)

  def RouteNotFound(badVal: String): RouteMessage =
    RouteMessage(badVal)
