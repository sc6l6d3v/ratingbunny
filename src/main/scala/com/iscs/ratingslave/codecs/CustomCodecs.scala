package com.iscs.ratingslave.codecs

import com.iscs.ratingslave.domains.ImdbQuery.{AutoNameRec, AutoTitleRec, TitleRec}
import mongo4cats.codecs.{CodecRegistry => mcodecRegistry}
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.bson.codecs.configuration.{CodecProvider, CodecRegistry}
import org.mongodb.scala.bson.codecs.Macros
import org.mongodb.scala.{MongoClient => mclient}

trait CustomCodecs {
  val autoNameRecCodecProvider: CodecProvider = Macros.createCodecProvider[AutoNameRec]()
  val autoTitleRecCodecProvider: CodecProvider = Macros.createCodecProvider[AutoTitleRec]()
  val titleRecCodecProvider: CodecProvider = Macros.createCodecProvider[TitleRec]()

  val codecRegistry: CodecRegistry =
    fromRegistries(
      fromProviders(
        autoNameRecCodecProvider,
        autoTitleRecCodecProvider,
        titleRecCodecProvider
      ),
      mclient.DEFAULT_CODEC_REGISTRY
    )

  val getRegistry: mcodecRegistry = mcodecRegistry.mergeWithDefault(codecRegistry)
}
