package com.iscs.releaseScraper.util

import java.awt.image.BufferedImage
import java.io.InputStream

import cats.effect.{Blocker, ConcurrentEffect, ContextShift, Resource, Sync, Timer}
import javax.imageio.ImageIO

class ResourceProcessor[F[_]: ConcurrentEffect](resourceName: String)(implicit CS: ContextShift[F], S: Sync[F], T: Timer[F]) {

  private def readInpStream(is: InputStream, blocker: Blocker): F[BufferedImage] = {
    blocker.delay[F, BufferedImage] {
      ImageIO.read(is)
    }
  }

  private def imagereader(file: String): Resource[F, InputStream] =
    Resource.fromAutoCloseable(S.delay { getClass.getClassLoader.getResourceAsStream(file) })

  def readImageFromFile(blocker: Blocker): F[BufferedImage] =
    imagereader(resourceName: String).use(is => readInpStream(is, blocker))
}
