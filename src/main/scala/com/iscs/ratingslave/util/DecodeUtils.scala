package com.iscs.ratingslave.util

import cats.effect.kernel.Sync

import scala.util.Try

object DecodeUtils {
  def getRating[F[_]: Sync](rating: String): F[Double] =
    Sync[F].delay(Try(rating.toDouble).toOption.getOrElse(5.0D))
}
