package com.iscs.ratingslave.codecs

import com.iscs.ratingslave.domains.ImdbQuery.AutoNameRec.autoNameRecCodecProvider
import com.iscs.ratingslave.domains.ImdbQuery.AutoTitleRec.autoTitleRecCodecProvider
import com.iscs.ratingslave.domains.ImdbQuery.{AutoNameRec, AutoTitleRec, TitleRec}
import com.iscs.ratingslave.domains.ImdbQuery.TitleRec.titleRecCodecProvider
import mongo4cats.codecs.MongoCodecProvider
import org.bson.codecs.configuration.CodecProvider
import zio.json._

import scala.reflect.ClassTag

trait CustomCodecs {
  val titleRecClass = classOf[TitleRec]
  val autoNameRecClass = classOf[AutoNameRec]
  val autoTitleRecClass = classOf[AutoTitleRec]
  implicit def zioJsonBasedCodecProvider[T: JsonEncoder: JsonDecoder: ClassTag]: MongoCodecProvider[T] =
    new MongoCodecProvider[T] {
      implicit val classT: Class[T] = implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]]
      override def get: CodecProvider = {
        classT match {
          case `titleRecClass`     => titleRecCodecProvider
          case `autoTitleRecClass` => autoTitleRecCodecProvider
          case `autoNameRecClass`  => autoNameRecCodecProvider
          case _                   => titleRecCodecProvider
        }
      }
    }
}
