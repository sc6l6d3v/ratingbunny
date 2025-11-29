package com.iscs.ratingbunny.domains

import com.iscs.ratingbunny.model.Requests.ReqParams
import com.typesafe.scalalogging.Logger
import mongo4cats.bson.{BsonValueEncoder, Document}
import mongo4cats.bson.BsonValueEncoder.*
import mongo4cats.bson.syntax.*
import org.bson.conversions.Bson
import org.mongodb.scala.model.Projections.{computed, exclude, fields, include}
import org.mongodb.scala.model.{Aggregates, Projections, Sorts}
import scala.language.implicitConversions

trait QuerySetup:
  private val id               = "_id"
  private val averageRating    = "rating.average"
  private val numVotes         = "rating.votes"
  private val genres           = "genres"
  private val primaryName      = "primaryName"
  private val primaryTitle     = "primaryTitle"
  private val primaryTitleLC   = "primaryTitleLC"
  private val nameLC           = "nameLC"
  private val isAdult          = "isAdult"
  private val startYear        = "startYear"
  private val endYear          = "endYear"
  private val runtimeMinutes   = "runtimeMinutes"
  private val titleType        = "titleType"
  private val langMask         = "langMask"
  private val role             = "role"
  private val nconst           = "nconst"
  private val hasUS            = "hasUS"
  private val hasEN            = "hasEN"
  private val STREAMLIMIT      = 96
  private val AUTOSUGGESTLIMIT = 20
  private val AUTONAME_LIMIT   = 10
  protected val autoSuggestLimit: Int = AUTOSUGGESTLIMIT
  private val EXACT            = "exact"
  private val GTE              = "$gte"
  private val LTE              = "$lte"
  private val LT               = "$lt"
  private val IN               = "$in"
  private val OR               = "$or"
  private val REGX             = "$regex"
  private val BITSANY          = "$bitsAnySet"
  private val autoTitleHint    = "auto_movie_genre_prefix_langMask"
  private val topLangs         = List("en", "ja", "de", "fr")
  val L: Logger                = Logger[this.type]

  extension (b: Boolean) def toInt: Int = if b then 1 else 0

  given BsonValueEncoder[Int]    = BsonValueEncoder.intEncoder
  given BsonValueEncoder[Double] = BsonValueEncoder.doubleEncoder
  given BsonValueEncoder[Long]   = BsonValueEncoder.longEncoder

  private def gte[A](fieldName: String, a: A)(using encoder: BsonValueEncoder[A]) = Document(fieldName := Document(GTE := a))
  def feq[A](fieldName: String, a: A)(using encoder: BsonValueEncoder[A])         = Document(fieldName := a)
  private def regx(fieldName: String, pattern: String)                            = Document(fieldName := Document(REGX := pattern))
  private def or(docs: List[Document])                                            = Document(OR := docs)

  private def between(fieldName: String, startyear: Int, stopyear: Int): Document =
    Document(fieldName := Document(GTE := startyear).add(LTE := stopyear))

  private def prefixRange(fieldName: String, prefix: String): Document =
    Document(fieldName := Document(GTE := prefix.toLowerCase).add(LT := s"${prefix.toLowerCase}\uffff"))

  private def optionalLangBit(lang: Option[String]): Option[Long] =
    lang.flatMap: code =>
      val idx = topLangs.indexOf(code.toLowerCase)
      Option.when(idx >= 0)(1L << idx)

  private def mergeDocs(docs: List[Document]): Document = docs.foldLeft(Document())(_ merge _)

  /** Produce a sort Bson based on the enum choice. */
  private def buildSort(sortField: SortField): Bson = sortField match
    case SortField.ByRating =>
      Sorts.orderBy(
        Sorts.descending(hasUS, numVotes, averageRating),
        Sorts.descending(startYear),
        Sorts.ascending(primaryTitle)
      )
    case SortField.ByYear =>
      Sorts.orderBy(
        Sorts.descending(startYear, numVotes, averageRating),
        Sorts.ascending(primaryTitle)
      )
    case SortField.ByTitle =>
      Sorts.orderBy(
        Sorts.ascending(primaryTitle),
        Sorts.descending(startYear, numVotes, averageRating)
      )
    case SortField.ByVotes =>
      Sorts.orderBy(
        Sorts.descending(numVotes, averageRating, startYear),
        Sorts.ascending(primaryTitle)
      )

  private def limitBson(limit: Int): Bson = Aggregates.limit(limit)

  private def getOptFilters(optTitle: Option[String], searchType: Option[String]): Option[Document] =
    optTitle.map: title =>
      searchType match
        case Some(EXACT) => Document(primaryTitle := title)
        case _           => prefixRange(primaryTitleLC, title)

  private def getParamList(params: ReqParams): List[Document] =
    List(
      params.isAdult.map(isAdlt => Document(isAdult := isAdlt.toInt)),
      params.year.map(yr => between(startYear, yr.head, yr.last)),
      params.genre.map(genre => Document(genres := Document(IN := genre))),
      params.titleType.map(tt => Document(titleType := Document(IN := tt))),
      params.query.flatMap(title => getOptFilters(params.query, params.searchType))
    ).flatten

  def genAutonameFilter(namePrefix: String, lowYear: Int, highYear: Int): Seq[Bson] =
    val matchBson = mergeDocs(
      List(
        prefixRange(nameLC, namePrefix),
        Document(OR := List(Document("birthYear" := Document(LTE := highYear)), Document("birthYear" := null))),
        Document(OR := List(Document("deathYear" := Document(GTE := lowYear)), Document("deathYear" := null)))
      )
    )
    val sortElt = Sorts.ascending(nameLC)
    val proj    = fields(include(id, primaryName, "birthYear", "deathYear", "primaryProfession"))

    Seq(
      Aggregates.`match`(matchBson),
      Aggregates.sort(sortElt),
      Aggregates.project(proj),
      Aggregates.limit(AUTONAME_LIMIT)
    )

  final case class AutotitleSpec(matchBson: Document, projectBson: Bson, sortBson: Bson, hint: String)

  def genAutotitleFilter(titlePrefix: String, lang: Option[String], rating: Double, votes: Int, params: ReqParams): AutotitleSpec =
    val fuzzyParams   = params.copy(query = None) // force to not parse primaryTitle
    val titleElt      = prefixRange(primaryTitleLC, titlePrefix)
    val ratingFilter  = gte(averageRating, rating)
    val votesFilter   = gte(numVotes, votes.toDouble)
    val langMaskMatch = optionalLangBit(lang).map(bit => Document(langMask := Document(BITSANY := bit)))

    val matchBson: Document = mergeDocs(
      List(titleElt, ratingFilter, votesFilter) ::: langMaskMatch.toList ::: getParamList(fuzzyParams)
    )

    val sortBson = Sorts.ascending(primaryTitle)

    val projectBson = fields(
      include(id, primaryTitle, startYear, "rating")
    )

    AutotitleSpec(matchBson, projectBson, sortBson, autoTitleHint)

  def genNameFilter(nm: String, rating: Double, params: ReqParams, langBit: Option[Long] = None): Document =
    val langMaskFilter = langBit.map(bit => Document(langMask := Document(BITSANY := bit)))
    val filters = List(Some(feq(nconst, nm)), langMaskFilter, Some(combineVotesWithRating(params, rating))) :::
      getParamList(params).map(Some(_))
    mergeDocs(filters.flatten)

  def genTitleFilter(params: ReqParams, rating: Double, langBit: Option[Long] = None): Document =
    val langMaskFilter = langBit.map(bit => Document(langMask := Document(BITSANY := bit)))
    mergeDocs((langMaskFilter.toList ::: List(combineVotesWithRating(params, rating)) ::: getParamList(params)))

  private def combineVotesWithRating(params: ReqParams, rating: Double): Document =
    params.votes match
      case Some(realVotes) =>
        or(
          List(
            gte(numVotes, realVotes.toDouble).merge(gte(averageRating, rating)),
            feq(numVotes, 0.0).merge(feq(averageRating, 0.0))
          )
        )
      case _ =>
        or(
          List(
            gte(averageRating, rating),
            feq(averageRating, 0.0)
          )
        )

  def genQueryPipeline(
      matchVariable: Bson,
      isLimited: Boolean = false,
      limit: Int = STREAMLIMIT,
      sortField: SortField = SortField.ByRating,
      projection: Option[Bson] = None
  ): Seq[Bson] =
    val basePipeLine = Seq(
      Some(Aggregates.`match`(matchVariable)),
      projection.map(Aggregates.project),
      Some(Aggregates.sort(buildSort(sortField)))
    ).flatten
    if (isLimited)
      basePipeLine :+ limitBson(limit)
    else
      basePipeLine

  def genTitleQueryPipeline(
      matchVariable: Bson,
      isLimited: Boolean = false,
      limit: Int = STREAMLIMIT,
      sortField: SortField = SortField.ByRating,
      projection: Option[Bson] = None
  ): Seq[Bson] =
    genQueryPipeline(matchVariable, isLimited, limit, sortField, projection)
