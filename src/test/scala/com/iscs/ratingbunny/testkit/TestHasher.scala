package com.iscs.ratingbunny.testkit

import cats.effect.{Async, Sync}
import com.iscs.ratingbunny.util.PasswordHasher

/** Hash = pw.reverse ; verify = equality after reverse.
 * Good enough for unit tests (⚠️ NEVER use in prod).
 */
object TestHasher:
  def make[F[_]: Async]: PasswordHasher[F] = new PasswordHasher[F]:
    def hash(pw: String): F[String] = Sync[F].pure(pw.reverse)
    def verify(pw: String, stored: String): F[Boolean] =
      Sync[F].pure(stored == pw.reverse)
