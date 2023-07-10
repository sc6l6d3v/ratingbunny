package com.iscs.ratingslave.dslparams

import org.http4s.dsl.io._
object WindowWidthQueryParameterMatcher extends QueryParamDecoderMatcher[String]("ws")
object WindowHeightQueryParameterMatcher extends QueryParamDecoderMatcher[String]("wh")

