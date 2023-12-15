package com.iscs.ratingslave.util

import cats.effect.kernel.Sync

import scala.util.Try

trait DecodeUtils {
  def getRating[F[_]: Sync](rating: String): F[Double] =
    Sync[F].delay(Try(rating.toDouble).toOption.getOrElse(5.0D))

  def protoc(imageHost: String): String =
    if (List("tmdbimg", "localhost", "192.168.4")
      .exists(host => imageHost startsWith host))
      "http"
    else
      "https"
}
