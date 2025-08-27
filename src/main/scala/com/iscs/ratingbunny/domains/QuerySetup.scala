package com.iscs.ratingbunny.domains

import com.iscs.ratingbunny.model.Requests.ReqParams
import com.typesafe.scalalogging.Logger
import mongo4cats.bson.{BsonValueEncoder, Document}
import mongo4cats.bson.BsonValueEncoder.*
import mongo4cats.bson.syntax.*
import org.bson.conversions.Bson
import org.mongodb.scala.model.Projections.{computed, exclude, fields, include}
import org.mongodb.scala.model.{Accumulators, Aggregates, BsonField, Projections, Sorts}
import scala.language.implicitConversions
import scala.util.matching.Regex.quote

trait QuerySetup:
  private val id               = "_id"
  private val averageRating    = "averageRating"
  private val firstName        = "firstName"
  private val genresList       = "genresList"
  private val lastName         = "lastName"
  private val isAdult          = "isAdult"
  private val numVotes         = "numVotes"
  private val tconst           = "tconst"
  private val primaryName      = "primaryName"
  private val originalTitle    = "originalTitle"
  private val primaryTitle     = "primaryTitle"
  private val startYear        = "startYear"
  private val endYear          = "endYear"
  private val runtimeMinutes   = "runtimeMinutes"
  private val titleType        = "titleType"
  private val STREAMLIMIT      = 96
  private val AUTOSUGGESTLIMIT = 20
  private val EXACT            = "exact"
  private val GTE              = "$gte"
  private val LTE              = "$lte"
  private val IN               = "$in"
  private val OR               = "$or"
  private val REGX             = "$regex"
  val L: Logger                = Logger[this.type]

  extension (b: Boolean) def toInt: Int = if b then 1 else 0

  given BsonValueEncoder[Int]    = BsonValueEncoder.intEncoder
  given BsonValueEncoder[Double] = BsonValueEncoder.doubleEncoder

  private def gte[A](fieldName: String, a: A)(using encoder: BsonValueEncoder[A]) = Document(fieldName := Document(GTE := a))
  def feq[A](fieldName: String, a: A)(using encoder: BsonValueEncoder[A])         = Document(fieldName := a)
  private def regx[A](fieldName: String, pattern: String)                         = Document(fieldName := Document(REGX := pattern))
  private def or(docs: List[Document])                                            = Document(OR := docs)

  private def between(fieldName: String, startyear: Int, stopyear: Int): Document =
    Document(fieldName := Document(GTE := startyear).add(LTE := stopyear))

  private def buildAccums(fields: List[String]): Seq[BsonField] = fields
    .map(field => Accumulators.first(field, s"$$$field"))

  private val groupAccums: Seq[BsonField] = buildAccums(
    List(averageRating, numVotes, titleType, primaryTitle, originalTitle, isAdult, startYear, endYear, runtimeMinutes, genresList)
  )

  private val projections: Bson = getProjections(
    computes = List(averageRating, originalTitle, numVotes, runtimeMinutes),
    includes = List(titleType, primaryTitle, isAdult, startYear, endYear, genresList),
    excludes = List.empty[String]
  )

  private def getProjections(computes: List[String], includes: List[String], excludes: List[String]): Bson =
    val fields = computes.map(comp => Projections.computed(comp, Document("$ifNull" := List(s"$$$comp", "$$REMOVE")))) ++
      List(Projections.include(includes*)) ++
      List(Projections.exclude(excludes*))
    Projections.fields(fields*)

  /** Produce a sort Bson based on the enum choice. */
  private def buildSort(sortField: SortField): Bson = sortField match
    case SortField.ByRating =>
      // Original logic: rating desc, year desc, votes desc, then title asc
      Sorts.orderBy(
        Sorts.descending(averageRating, startYear, numVotes),
        Sorts.ascending(primaryTitle)
      )
    case SortField.ByYear =>
      // Put year first, then rating, votes, etc.
      Sorts.orderBy(
        Sorts.descending(startYear, averageRating, numVotes),
        Sorts.ascending(primaryTitle)
      )
    case SortField.ByTitle =>
      // Put title first ascending, then rating/yr/votes descending, for instance
      Sorts.orderBy(
        Sorts.ascending(primaryTitle),
        Sorts.descending(startYear, averageRating, numVotes)
      )
    case SortField.ByVotes =>
      // Put votes first, then rating/year, then title
      Sorts.orderBy(
        Sorts.descending(numVotes, averageRating, startYear),
        Sorts.ascending(primaryTitle)
      )

  private def limitBson(limit: Int): Bson = Aggregates.limit(limit)

  private def getOptFilters(optTitle: Option[String], searchType: Option[String]): Option[Document] =
    optTitle.map: title =>
      searchType match
        case Some(EXACT) => Document(primaryTitle := title)
        case _ =>
          val quotedQuery = quote(title)
          Document(primaryTitle := Document(REGX := s"^$quotedQuery"))

  private def getParamList(params: ReqParams): List[Document] =
    List(
      params.isAdult.map(isAdlt => Document(isAdult := isAdlt.toInt)),
      params.year.map(yr => between(startYear, yr.head, yr.last)),
      params.genre.map(genre => Document(genresList := Document(IN := genre))),
      params.titleType.map(tt => Document(titleType := Document(IN := tt))),
      params.query.flatMap(title => getOptFilters(params.query, params.searchType))
    ).flatten

  private def splitTwoRest(params: ReqParams): (List[Document], List[Document]) =
    getParamList(params) match
      case head :: next :: rest => (List(head, next), rest)
      case head :: Nil          => (List(head), Nil)
      case Nil                  => (Nil, Nil)

  def genAutonameFilter(namePrefix: String, rating: Double, params: ReqParams): Seq[Bson] =
    val names =
      if (namePrefix.contains(" "))
        namePrefix.split(" ").toList
      else
        List(namePrefix)

    val lastFirstRegex =
      if (names.size == 1)
        regx(lastName, s"""^${names.head}""")
      else
        regx(lastName, s"""^${names.last}""").merge(feq(firstName, names.head))

    val emptyQuery          = params.copy(query = None) // force to skip primaryTitle
    val matchBson: Document = computeBson(rating, params, lastFirstRegex, emptyQuery)

    val sortElt = Sorts.ascending(id, firstName)

    val lastProjectionsBson = fields(
      exclude(id),
      include(firstName),
      computed(lastName, "$_id")
    )

    Seq(
      Aggregates.`match`(matchBson),
      Aggregates.group("$lastName", Accumulators.first(firstName, s"$$$firstName")),
      Aggregates.sort(sortElt),
      Aggregates.project(lastProjectionsBson),
      Aggregates.limit(AUTOSUGGESTLIMIT)
    )

  private def computeBson(rating: Double, params: ReqParams, lastFirstRegex: Document, emptyQuery: ReqParams): Document =
    val combinedOrFilter      = combineVotesWithRating(params, rating)
    val (firstTwo, remaining) = splitTwoRest(emptyQuery)
    val nonEmptyElts          = firstTwo ::: List(combinedOrFilter) ::: remaining ::: List(lastFirstRegex)
    val matchBson             = nonEmptyElts.reduce(_ merge _)
    matchBson

  def genAutotitleFilter(titlePrefix: String, rating: Double, params: ReqParams): Seq[Bson] =
    val fuzzyParams = params.copy(query = None) // force to not parse primaryTitle
    val titleElt    = regx(primaryTitle, s"""^$titlePrefix""")

    val matchBson: Document = computeBson(rating, params, titleElt, fuzzyParams)

    val sortBson = Sorts.ascending(primaryTitle)

    val projectBson = fields(
      exclude(id),
      computed(primaryTitle, "$_id")
    )

    Seq(
      Aggregates.`match`(matchBson),
      Aggregates.group("$primaryTitle"),
      Aggregates.project(projectBson),
      Aggregates.sort(sortBson),
      Aggregates.limit(AUTOSUGGESTLIMIT)
    )

  def genNameFilter(name: String, rating: Double, params: ReqParams): Document =
    val combinedOrFilter             = combineVotesWithRating(params, rating)
    val emptyTitle                   = params.copy(query = None)
    val (firstTwo, remaining)        = splitTwoRest(emptyTitle)
    val nonEmptyElts: List[Document] = firstTwo ::: List(combinedOrFilter) ::: remaining ::: List(feq(primaryName, name))
    nonEmptyElts.reduce(_ merge _)

  def genTitleFilter(params: ReqParams, rating: Double): Document =
    val combinedOrFilter      = combineVotesWithRating(params, rating)
    val (firstTwo, remaining) = splitTwoRest(params)
    (firstTwo ::: List(combinedOrFilter) ::: remaining).reduce(_ merge _)

  private def combineVotesWithRating(params: ReqParams, rating: Double): Document =
    params.votes match
      case Some(realVotes) =>
        or(
          List(
            gte(numVotes, realVotes).merge(gte(averageRating, rating)),
            feq(numVotes, 0).merge(feq(averageRating, 0.0))
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
      sortField: SortField = SortField.ByRating
  ): Seq[Bson] =
    val basePipeLine = Seq(
      Aggregates.`match`(matchVariable),
      Aggregates.group(s"$$$tconst", groupAccums*),
      Aggregates.project(projections),
      Aggregates.sort(buildSort(sortField))
    )
    if (isLimited)
      basePipeLine :+ limitBson(limit)
    else
      basePipeLine

  def genTitleQueryPipeline(
      matchVariable: Bson,
      isLimited: Boolean = false,
      limit: Int = STREAMLIMIT,
      sortField: SortField = SortField.ByRating
  ): Seq[Bson] =
    val basePipeLine = Seq(
      Aggregates.`match`(matchVariable),
      Aggregates.project(projections),
      Aggregates.sort(buildSort(sortField))
    )
    if (isLimited)
      basePipeLine :+ limitBson(limit)
    else
      basePipeLine
