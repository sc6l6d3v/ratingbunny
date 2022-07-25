package com.iscs.ratingslave.util

import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Projections._

object ProjectionUtils {
  def getProjectionFields(projSet: Map[String, Boolean]): List[Bson] =
    projSet.map{ case(field, action) =>
      if (action) include(field) else exclude(field)
    }.toList

  def getProjections(projSet: List[Bson]): Bson = fields(projSet:_*)
}
