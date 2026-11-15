package com.iscs.ratingbunny.testkit

import java.net.ServerSocket

/** Asks the OS for a currently unused TCP port so each embedded-Mongo suite listens on its own port. Suites run in parallel in the
  * forked test JVM, so a fixed port shared by two suites makes mongod fail to bind.
  */
object FreePort:
  def apply(): Int =
    val socket = new ServerSocket(0)
    try socket.getLocalPort
    finally socket.close()
