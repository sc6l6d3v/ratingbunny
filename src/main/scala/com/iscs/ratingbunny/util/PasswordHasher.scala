package com.iscs.ratingbunny.util

import cats.effect.Async
import org.mindrot.jbcrypt.BCrypt

trait PasswordHasher[F[_]]:
  def hash(pw: String): F[String]
  def verify(pw: String, stored: String): F[Boolean]

object BcryptHasher:

  def make[F[_]: Async](cost: Int = 12): PasswordHasher[F] =
    new PasswordHasher[F]:
      def hash(pw: String): F[String] =
        Async[F].blocking(BCrypt.hashpw(pw, BCrypt.gensalt(cost)))

      def verify(pw: String, stored: String): F[Boolean] =
        Async[F].blocking(BCrypt.checkpw(pw, stored))
