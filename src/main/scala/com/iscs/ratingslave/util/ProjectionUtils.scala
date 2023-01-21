package com.iscs.ratingslave.util

import mongo4cats.operations.Projection
import mongo4cats.operations.Projection.{exclude, include}
object ProjectionUtils {
  def getProjectionFields(projSet: Map[String, Boolean]): List[Projection] =
    projSet.map{ case(field, action) =>
      if (action) include(field) else exclude(field)
    }.toList

  def getProjections(projSet: List[Projection]): Projection = projSet.reduce { _ combinedWith _  }
}
