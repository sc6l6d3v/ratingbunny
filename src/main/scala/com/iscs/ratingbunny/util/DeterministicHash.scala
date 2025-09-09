package com.iscs.ratingbunny.util

import java.security.MessageDigest
import java.nio.charset.StandardCharsets

object DeterministicHash:
  def sha256(s: String): String =
    MessageDigest
      .getInstance("SHA-256")
      .digest(s.getBytes(StandardCharsets.UTF_8))
      .map("%02x" format _)
      .mkString
