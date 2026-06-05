package com.iscs.ratingbunny.domains

import cats.effect.*
import cats.implicits.*
import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import mongo4cats.circe.*
import mongo4cats.collection.MongoCollection

trait TitleRanges[F[_]]:
  def getAll: F[List[TitleRange]]

class TitleRangesImpl[F[_]: Async](
    coll: MongoCollection[F, TitleRange]
) extends TitleRanges[F]:

  override def getAll: F[List[TitleRange]] =
    coll.find.all.map(_.toList)
