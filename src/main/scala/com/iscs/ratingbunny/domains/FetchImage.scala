package com.iscs.ratingbunny.domains

import cats.effect.kernel.Clock
import cats.effect.{Async, Sync}
import cats.implicits.*
import com.iscs.ratingbunny.util.DecodeUtils
import com.typesafe.scalalogging.Logger
import fs2.Stream
import org.http4s.client.Client
import org.http4s.{Method, Request, Uri}
import scodec.bits.ByteVector

class FetchImage[F[_]: Async](defaultHost: String, imageHost: String, client: Client[F]) extends DecodeUtils {
  private val L         = Logger[this.type]
  private val proto     = protoc(imageHost)
  private val metaImage = s"$proto://$imageHost/meta"
  L.info(s"imageHost {} metaImage {}", imageHost, metaImage)

  def getImage(imdb: String): Stream[F, Byte] = for {
    imgStr <- Stream.eval(Sync[F].delay(s"""$metaImage/$imdb/S"""))
    imgReq <- Stream.eval(Sync[F].delay(Request[F](Method.GET, Uri.unsafeFromString(imgStr))))
    result <- Stream.eval(
      Clock[F]
        .timed(
          client
            .run(imgReq)
            .use(resp =>
              resp.body.compile
                .to(ByteVector)
                .map(_.toArray)
            )
        )
        .attempt
    )
    imgStream <- result match {
      case Right((imgTime, imgBytes)) =>
        Stream.eval(Sync[F].delay(L.info(s"imgStr {} image id {} size {} time {} ms", imgStr, imdb, imgBytes.length, imgTime.toMillis))) >>
          Stream.emits(imgBytes)
      case Left(error) =>
        Stream.eval(Sync[F].delay(L.error(s"Error fetching image for imdb $imdb from $imgStr: ${error.getMessage}", error))) >>
          Stream.empty
    }
  } yield imgStream
}
