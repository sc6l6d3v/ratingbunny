package com.iscs.ratingbunny.domains

import cats.effect.{IO, Resource}
import cats.effect.unsafe.IORuntime
import com.iscs.ratingbunny.domains.ImdbQueryData.{
  autosuggestNameRecs,
  autosuggestTitleRecs,
  enhancedNameCompRecs,
  titlePathRecs,
  titleRecs
}
import com.iscs.ratingbunny.domains.PagedTitleData.pagedTitleRecs
import com.iscs.ratingbunny.model.Requests.ReqParams
import com.typesafe.scalalogging.Logger
import io.circe.generic.auto.*
import mongo4cats.circe.*
import mongo4cats.client.MongoClient
import mongo4cats.collection.MongoCollection
import mongo4cats.database.MongoDatabase
import mongo4cats.embedded.EmbeddedMongo
import mongo4cats.bson.Document
import mongo4cats.operations.Index
import munit.CatsEffectSuite
import org.http4s.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityCodec.circeEntityEncoder
import org.http4s.client.Client
import org.http4s.dsl.io.*
import com.mongodb.client.model.{Filters, IndexOptions as JIndexOptions}

import scala.concurrent.Future
import scala.concurrent.duration.*

class ImdbQuerySpec extends CatsEffectSuite with EmbeddedMongo:
  private val L                         = Logger[this.type]
  override def munitIOTimeout: Duration = 2.minutes

  override val mongoPort: Int        = 12348
  val mockHttpClient                 = Option.empty[Client[IO]]
  private val peopleCollection       = "people"
  private val peopleTitlesCollection = "people_titles"
  private val titlesCollection       = "titles"
  private val autoTitleIndexName        = "auto_movie_genre_prefix_langMask"
  private val autoTitleNoGenreIndexName = "auto_titleLC_type_langMask"

  test("getByTitle should return results for valid title"):
    withEmbeddedMongoClient: mongoclient =>
      for
        db             <- setupTestDatabase("test", mongoclient)
        peopleFx       <- setupAutoNameCollection(db, peopleCollection)
        peopleTitlesFx <- setupTitleCollection(db, peopleTitlesCollection)
        titlesDocsFx   <- setupDocumentCollection(db, titlesCollection)
        titlesFx       <- setupTitleCollection(db, titlesCollection)
        autoTitlesFx   <- setupAutoTitleCollection(db, titlesCollection)
        _ <- titlesDocsFx
          .insertMany(titleRecs)
          .attempt
          .flatMap:
            case Left(e) =>
              IO(L.error(s"failed to insert ${titleRecs.mkString}")) >> IO.raiseError(e)
            case Right(_) =>
              IO(L.info(s"succeeded inserts ${titleRecs.mkString}"))
        imdbQuery    = new ImdbQueryImpl[IO](peopleFx, peopleTitlesFx, titlesFx, autoTitlesFx, "localhost", mockHttpClient)
        title        = Some("The Shawshank Redemption")
        params       = ReqParams(query = title, sortType = Some("rating"))
        resultStream = imdbQuery.getByTitle(8.0, params, offset = 0, limit = 10, SortField.from(params.sortType))
        results <- resultStream.compile.toList
      yield
        assert(results.nonEmpty)
        assert(results.forall(_.primaryTitle.contains("The Shawshank Redemption")))

  test("getByTitlePath should return results with paths"):
    withEmbeddedMongoClient: mongoclient =>

      val mockHttpClient: Client[IO] = Client[IO]:
        case GET -> Root / "path" / "tt0234215" / "S" =>
          Resource.pure(Response[IO](Status.Ok).withEntity(PathRec("/9TGHDvWrqKBzwDxDodHYXEmOE6J.jpg")))
        case GET -> Root / "path" / "tt0242653" / "S" =>
          Resource.pure(Response[IO](Status.Ok).withEntity(PathRec("/t1wm4PgOQ8e4z1C6tk1yDNrps4T.jpg")))
        case GET -> Root / "path" / "tt0133093" / "S" =>
          Resource.pure(Response[IO](Status.Ok).withEntity(PathRec("/f89U3ADr1oiB1s9GkdPOEpXUk5H.jpg")))
        case _ =>
          Resource.pure(Response[IO](Status.NotFound))

      for
        db             <- setupTestDatabase("test", mongoclient)
        peopleFx       <- setupAutoNameCollection(db, peopleCollection)
        peopleTitlesFx <- setupTitleCollection(db, peopleTitlesCollection)
        titlesDocsFx   <- setupDocumentCollection(db, titlesCollection)
        titlesFx       <- setupTitleCollection(db, titlesCollection)
        autoTitlesFx   <- setupAutoTitleCollection(db, titlesCollection)
        _ <- titlesDocsFx
          .insertMany(titlePathRecs)
          .attempt
          .flatMap:
            case Left(e) =>
              IO(L.error(s"failed to insert ${titlePathRecs.mkString}")) >> IO.raiseError(e)
            case Right(_) =>
              IO(L.info(s"succeeded inserts ${titlePathRecs.mkString}"))
        imdbQuery    = new ImdbQueryImpl[IO](peopleFx, peopleTitlesFx, titlesFx, autoTitlesFx, "localhost", Some(mockHttpClient))
        title        = Some("The Matrix")
        params       = ReqParams(query = title, sortType = Some("rating"))
        resultStream = imdbQuery.getByTitlePath(7.5, params, offset = 0, limit = 10, SortField.from(params.sortType))
        results <- resultStream.compile.toList
      yield
        assert(results.nonEmpty)
        assert(results.forall(_.primaryTitle.contains("The Matrix")))
        assert(results.forall(_.posterPath.nonEmpty))

  test("getByEnhancedName should return enhanced results for valid name"):
    withEmbeddedMongoClient: mongoclient =>
      for
        db                 <- setupTestDatabase("test", mongoclient)
        peopleFx           <- setupAutoNameCollection(db, peopleCollection)
        peopleTitlesDocsFx <- setupDocumentCollection(db, peopleTitlesCollection)
        peopleTitlesFx     <- setupTitleCollection(db, peopleTitlesCollection)
        titlesFx           <- setupTitleCollection(db, titlesCollection)
        autoTitlesFx       <- setupAutoTitleCollection(db, titlesCollection)
        _ <- peopleTitlesDocsFx
          .insertMany(enhancedNameCompRecs)
          .attempt
          .flatMap:
            case Left(e) =>
              IO(L.error(s"failed to insert ${enhancedNameCompRecs.mkString}")) >> IO.raiseError(e)
            case Right(_) =>
              IO(L.info(s"succeeded inserts ${enhancedNameCompRecs.mkString}"))
        imdbQuery = new ImdbQueryImpl[IO](peopleFx, peopleTitlesFx, titlesFx, autoTitlesFx, "localhost", mockHttpClient)
        params    = ReqParams(sortType = Some("rating"))
        // name         = "Leonardo DiCaprio"
        name         = "nm0000138"
        resultStream = imdbQuery.getByEnhancedName(name, 6.5, params, offset = 0, limit = 5, SortField.from(params.sortType))
        results <- resultStream.compile.toList
      yield
        assert(results.nonEmpty)
        assert(results.forall(_.primaryTitle.nonEmpty))

  test("getByTitle reads one page at a time and countByTitle counts the whole match"):
    withEmbeddedMongoClient: mongoclient =>
      for
        db             <- setupTestDatabase("test", mongoclient)
        peopleFx       <- setupAutoNameCollection(db, peopleCollection)
        peopleTitlesFx <- setupTitleCollection(db, peopleTitlesCollection)
        titlesDocsFx   <- setupDocumentCollection(db, titlesCollection)
        titlesFx       <- setupTitleCollection(db, titlesCollection)
        autoTitlesFx   <- setupAutoTitleCollection(db, titlesCollection)
        _              <- titlesDocsFx.insertMany(pagedTitleRecs)
        imdbQuery = new ImdbQueryImpl[IO](peopleFx, peopleTitlesFx, titlesFx, autoTitlesFx, "localhost", mockHttpClient)
        params    = ReqParams(year = Some(List(2000, 2100)), sortType = Some("rating"))
        all     <- imdbQuery.getByTitle(5.0, params, offset = 0, limit = 100, SortField.ByRating).compile.toList
        page1   <- imdbQuery.getByTitle(5.0, params, offset = 0, limit = 10, SortField.ByRating).compile.toList
        page2   <- imdbQuery.getByTitle(5.0, params, offset = 10, limit = 10, SortField.ByRating).compile.toList
        page3   <- imdbQuery.getByTitle(5.0, params, offset = 20, limit = 10, SortField.ByRating).compile.toList
        pastEnd <- imdbQuery.getByTitle(5.0, params, offset = 30, limit = 10, SortField.ByRating).compile.toList
        total   <- imdbQuery.countByTitle(5.0, params)
        noYear  <- imdbQuery.countByTitle(5.0, params.copy(year = Some(Nil)))
        strict  <- imdbQuery.countByTitle(8.0, params)
      yield
        assertEquals(all.map(_._id), pagedTitleRecs.flatMap(_.getString("_id")))
        assertEquals(page1.map(_._id), all.take(10).map(_._id))
        assertEquals(page2.map(_._id), all.slice(10, 20).map(_._id))
        assertEquals(page3.map(_._id), all.slice(20, 30).map(_._id))
        assertEquals(pastEnd, Nil)
        assertEquals(total, pagedTitleRecs.size.toLong)
        assertEquals(noYear, pagedTitleRecs.size.toLong)
        assertEquals(strict, all.count(_.rating.flatMap(_.average).exists(_ >= 8.0)).toLong)

  test("getByEnhancedName pages with an offset and countByName counts the whole match"):
    withEmbeddedMongoClient: mongoclient =>
      for
        db                 <- setupTestDatabase("test", mongoclient)
        peopleFx           <- setupAutoNameCollection(db, peopleCollection)
        peopleTitlesDocsFx <- setupDocumentCollection(db, peopleTitlesCollection)
        peopleTitlesFx     <- setupTitleCollection(db, peopleTitlesCollection)
        titlesFx           <- setupTitleCollection(db, titlesCollection)
        autoTitlesFx       <- setupAutoTitleCollection(db, titlesCollection)
        _                  <- peopleTitlesDocsFx.insertMany(enhancedNameCompRecs)
        imdbQuery = new ImdbQueryImpl[IO](peopleFx, peopleTitlesFx, titlesFx, autoTitlesFx, "localhost", mockHttpClient)
        params    = ReqParams(sortType = Some("rating"))
        name      = "nm0000138"
        all   <- imdbQuery.getByEnhancedName(name, 5.0, params, offset = 0, limit = 100, SortField.ByRating).compile.toList
        rest  <- imdbQuery.getByEnhancedName(name, 5.0, params, offset = 1, limit = 100, SortField.ByRating).compile.toList
        total <- imdbQuery.countByName(name, 5.0, params)
      yield
        assertEquals(all.size, 3)
        assertEquals(rest.map(_._id), all.drop(1).map(_._id))
        assertEquals(total, all.size.toLong)

  test("getAutosuggestTitle should return suggestions based on title prefix"):
    withEmbeddedMongoClient: mongoclient =>
      for
        db               <- setupTestDatabase("test", mongoclient)
        peopleFx         <- setupAutoNameCollection(db, peopleCollection)
        peopleTitlesFx   <- setupTitleCollection(db, peopleTitlesCollection)
        titlesFx         <- setupTitleCollection(db, titlesCollection)
        autoTitlesDocsFx <- setupDocumentCollection(db, titlesCollection)
        autoTitlesFx     <- setupAutoTitleCollection(db, titlesCollection)
        _ <- autoTitlesDocsFx
          .insertMany(autosuggestTitleRecs)
          .attempt
          .flatMap:
            case Left(e) =>
              IO(L.error(s"failed to insert ${autosuggestTitleRecs.mkString}")) >> IO.raiseError(e)
            case Right(_) =>
              IO(L.info(s"succeeded inserts ${autosuggestTitleRecs.mkString}"))
        _ <- ensureAutoTitleIndex(autoTitlesDocsFx)
        imdbQuery    = new ImdbQueryImpl[IO](peopleFx, peopleTitlesFx, titlesFx, autoTitlesFx, "localhost", mockHttpClient)
        params       = ReqParams()
        titlePrefix  = "Gone with the"
        resultStream = imdbQuery.getAutosuggestTitle(titlePrefix, None, params)
        results <- resultStream.compile.toList
      yield
        assert(results.nonEmpty)
        assert(results.forall(_.primaryTitle.startsWith("Gone with the")))

  test("getAutosuggestName should return suggestions based on name prefix"):
    withEmbeddedMongoClient: mongoclient =>
      for
        db             <- setupTestDatabase("test", mongoclient)
        peopleDocsFx   <- setupDocumentCollection(db, peopleCollection)
        peopleFx       <- setupAutoNameCollection(db, peopleCollection)
        peopleTitlesFx <- setupTitleCollection(db, peopleTitlesCollection)
        titlesFx       <- setupTitleCollection(db, titlesCollection)
        autoTitlesFx   <- setupAutoTitleCollection(db, titlesCollection)
        _ <- peopleDocsFx
          .insertMany(autosuggestNameRecs)
          .attempt
          .flatMap:
            case Left(e) =>
              IO(L.error(s"failed to insert ${autosuggestNameRecs.mkString}")) >> IO.raiseError(e)
            case Right(_) =>
              IO(L.info(s"succeeded inserts ${autosuggestNameRecs.mkString}"))
        imdbQuery    = new ImdbQueryImpl[IO](peopleFx, peopleTitlesFx, titlesFx, autoTitlesFx, "localhost", mockHttpClient)
        params       = ReqParams()
        namePrefix   = "John Cra"
        resultStream = imdbQuery.getAutosuggestName(namePrefix, Int.MinValue, Int.MaxValue)
        results <- resultStream.compile.toList
      yield
        assert(results.nonEmpty)
        assert(results.forall(_.primaryName.startsWith("John")))
        assert(results.forall(_.primaryName.contains("|")))

  def setupTestDatabase(name: String, client: MongoClient[IO]): IO[MongoDatabase[IO]] =
    client.getDatabase(name)

  def setupAutoNameCollection(db: MongoDatabase[IO], name: String): IO[MongoCollection[IO, AutoNameRec]] =
    db.getCollectionWithCodec[AutoNameRec](name)

  def setupAutoTitleCollection(db: MongoDatabase[IO], name: String): IO[MongoCollection[IO, AutoTitleRec]] =
    db.getCollectionWithCodec[AutoTitleRec](name)

  def setupDocumentCollection(db: MongoDatabase[IO], name: String): IO[MongoCollection[IO, Document]] =
    db.getCollectionWithCodec[Document](name)

  def setupTitleCollection(db: MongoDatabase[IO], name: String): IO[MongoCollection[IO, TitleRec]] =
    db.getCollectionWithCodec[TitleRec](name)

  private def ensureAutoTitleIndex(collection: MongoCollection[IO, Document]): IO[Unit] =
    for
      idxDocs <- collection.listIndexes
      existing = idxDocs.flatMap(_.getString("name").toList).toSet
      _ <-
        if existing(autoTitleIndexName) then IO.unit
        else
          val options = new JIndexOptions()
            .name(autoTitleIndexName)
            .background(true)
            .partialFilterExpression(Filters.eq("isAdult", Int.box(0)))
          collection
            .createIndex(
              Index
                .ascending("titleType")
                .ascending("genres")
                .ascending("primaryTitleLC")
                .ascending("langMask"),
              options
            )
            .void
      _ <-
        if existing(autoTitleNoGenreIndexName) then IO.unit
        else
          collection
            .createIndex(
              Index
                .ascending("primaryTitleLC")
                .ascending("titleType")
                .ascending("langMask"),
              new JIndexOptions().name(autoTitleNoGenreIndexName)
            )
            .void
    yield ()

  def withEmbeddedMongoClient[A](test: MongoClient[IO] => IO[A]): Future[A] =
    withRunningEmbeddedMongo:
      MongoClient
        .fromConnectionString[IO](s"mongodb://localhost:$mongoPort")
        .use(test)
    .unsafeToFuture()(IORuntime.global)
