package com.iscs.ratingbunny.dslparams

import org.http4s.dsl.io.*
object WindowWidthQueryParameterMatcher  extends QueryParamDecoderMatcher[String]("ws")
object WindowHeightQueryParameterMatcher extends QueryParamDecoderMatcher[String]("wh")
object CardWidthQueryParameterMatcher    extends QueryParamDecoderMatcher[String]("cs")
object CardHeightQueryParameterMatcher   extends QueryParamDecoderMatcher[String]("ch")
object OffsetQUeryParameterMatcher       extends QueryParamDecoderMatcher[String]("off")
