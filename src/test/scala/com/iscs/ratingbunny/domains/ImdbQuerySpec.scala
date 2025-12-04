package com.iscs.ratingbunny.domains

import cats.effect.{IO, Resource}
import cats.effect.unsafe.IORuntime
import com.iscs.ratingbunny.domains.ImdbQueryData.{
  autosuggestNameRecs,
  autosuggestTitleRecs,
  enhancedNameCompRecs,
  nameCompRecs,
  nameRecs,
  titlePathRecs,
  titleRecs
}
import com.iscs.ratingbunny.model.Requests.ReqParams
import com.typesafe.scalalogging.Logger
import fs2.Stream
import io.circe.generic.auto.*
import mongo4cats.circe.*
import mongo4cats.client.MongoClient
import mongo4cats.collection.MongoCollection
import mongo4cats.database.MongoDatabase
import mongo4cats.embedded.EmbeddedMongo
import mongo4cats.bson.Document
import munit.CatsEffectSuite
import org.http4s.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityCodec.circeEntityEncoder
import org.http4s.client.Client
import org.http4s.dsl.io.*

import scala.concurrent.Future
import scala.concurrent.duration.*

class ImdbQuerySpec extends CatsEffectSuite with EmbeddedMongo:
  private val L                         = Logger[this.type]
  override def munitIOTimeout: Duration = 2.minutes

  override val mongoPort: Int = 12348
  val mockHttpClient          = Option.empty[Client[IO]]

  test("getByTitle should return results for valid title"):
    withEmbeddedMongoClient: mongoclient =>
      for
        db            <- setupTestDatabase("test", mongoclient)
        peopleFx      <- setupAutoNameCollection(db, "autoNameRecords")
        peopleTitlesFx <- setupTitleCollection(db, "peopleTitleRecords")
        titlesDocsFx  <- setupDocumentCollection(db, "titleRecords")
        titlesFx      <- setupTitleCollection(db, "titleRecords")
        autoTitlesFx  <- setupAutoTitleCollection(db, "autoTitleRecords")
        _ <- titlesDocsFx
          .insertMany(titleRecs)
          .attempt
          .flatMap:
            case Left(e) =>
              IO(L.error(s"failed to insert ${titleRecs.mkString}")) >> IO.raiseError(e)
            case Right(_) =>
              IO(L.info(s"succeeded inserts ${titleRecs.mkString}"))
        imdbQuery = new ImdbQueryImpl[IO](peopleFx, peopleTitlesFx, titlesFx, autoTitlesFx, "localhost", mockHttpClient)
        title        = Some("The Shawshank Redemption")
        params       = ReqParams(query = title, sortType = Some("rating"))
        resultStream = imdbQuery.getByTitle(8.0, params, limit = 10, SortField.from(params.sortType))
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
        db            <- setupTestDatabase("test", mongoclient)
        peopleFx      <- setupAutoNameCollection(db, "autoNameRecords")
        peopleTitlesFx <- setupTitleCollection(db, "peopleTitleRecords")
        titlesDocsFx  <- setupDocumentCollection(db, "titleRecords")
        titlesFx      <- setupTitleCollection(db, "titleRecords")
        autoTitlesFx  <- setupAutoTitleCollection(db, "autoTitleRecords")
        _ <- titlesDocsFx
          .insertMany(titlePathRecs)
          .attempt
          .flatMap:
            case Left(e) =>
              IO(L.error(s"failed to insert ${titlePathRecs.mkString}")) >> IO.raiseError(e)
            case Right(_) =>
              IO(L.info(s"succeeded inserts ${titlePathRecs.mkString}"))
        imdbQuery = new ImdbQueryImpl[IO](peopleFx, peopleTitlesFx, titlesFx, autoTitlesFx, "localhost", Some(mockHttpClient))
        title        = Some("The Matrix")
        params       = ReqParams(query = title, sortType = Some("rating"))
        resultStream = imdbQuery.getByTitlePath(7.5, params, limit = 10, SortField.from(params.sortType))
        results <- resultStream.compile.toList
      yield
        assert(results.nonEmpty)
        assert(results.forall(_.primaryTitle.contains("The Matrix")))
        assert(results.forall(_.posterPath.nonEmpty))

  test("getByName should return results for valid name"):
    withEmbeddedMongoClient: mongoclient =>
      for
        db            <- setupTestDatabase("test", mongoclient)
        peopleFx      <- setupAutoNameCollection(db, "autoNameRecords")
        peopleTitlesDocsFx <- setupDocumentCollection(db, "title_principals_namerating")
        peopleTitlesFx <- setupTitleCollection(db, "title_principals_namerating")
        titlesDocsFx  <- setupDocumentCollection(db, "nameRecords")
        titlesFx      <- setupTitleCollection(db, "nameRecords")
        autoTitlesFx  <- setupAutoTitleCollection(db, "autoTitleRecords")
        _ <- peopleTitlesDocsFx
          .insertMany(nameCompRecs)
          .attempt
          .flatMap:
            case Left(e) =>
              IO(L.error(s"failed to insert ${nameCompRecs.mkString}")) >> IO.raiseError(e)
            case Right(_) =>
              IO(L.info(s"succeeded inserts ${nameCompRecs.mkString}"))
        _ <- titlesDocsFx
          .insertMany(nameRecs)
          .attempt
          .flatMap:
            case Left(e) =>
              IO(L.error(s"failed to insert ${nameRecs.mkString}")) >> IO.raiseError(e)
            case Right(_) =>
              IO(L.info(s"succeeded inserts ${nameRecs.mkString}"))

        imdbQuery = new ImdbQueryImpl[IO](peopleFx, peopleTitlesFx, titlesFx, autoTitlesFx, "localhost", mockHttpClient)
        params       = ReqParams(sortType = Some("rating"))
        name         = "Morgan Freeman"
        resultStream = imdbQuery.getByName(name, 6.5, params, SortField.from(params.sortType))
        results <- resultStream.compile.toList
      yield
        assert(results.nonEmpty)
        assert(results.forall(_.primaryTitle.nonEmpty))

  test("getByEnhancedName should return enhanced results for valid name"):
    withEmbeddedMongoClient: mongoclient =>
      for
        db            <- setupTestDatabase("test", mongoclient)
        peopleFx      <- setupAutoNameCollection(db, "autoNameRecords")
        peopleTitlesDocsFx <- setupDocumentCollection(db, "title_principals_namerating")
        peopleTitlesFx <- setupTitleCollection(db, "title_principals_namerating")
        titlesFx      <- setupTitleCollection(db, "enhancedNameRecords")
        autoTitlesFx  <- setupAutoTitleCollection(db, "autoTitleRecords")
        _ <- peopleTitlesDocsFx
          .insertMany(enhancedNameCompRecs)
          .attempt
          .flatMap:
            case Left(e) =>
              IO(L.error(s"failed to insert ${enhancedNameCompRecs.mkString}")) >> IO.raiseError(e)
            case Right(_) =>
              IO(L.info(s"succeeded inserts ${enhancedNameCompRecs.mkString}"))
        imdbQuery = new ImdbQueryImpl[IO](peopleFx, peopleTitlesFx, titlesFx, autoTitlesFx, "localhost", mockHttpClient)
        params       = ReqParams(sortType = Some("rating"))
        name         = "Leonardo DiCaprio"
        resultStream = imdbQuery.getByEnhancedName(name, 6.5, params, limit = 5, SortField.from(params.sortType))
        results <- resultStream.compile.toList
      yield
        assert(results.nonEmpty)
        assert(results.forall(_.primaryTitle.nonEmpty))

  test("getAutosuggestTitle should return suggestions based on title prefix"):
    withEmbeddedMongoClient: mongoclient =>
      for
        db            <- setupTestDatabase("test", mongoclient)
        peopleFx      <- setupAutoNameCollection(db, "autoNameRecords")
        peopleTitlesFx <- setupTitleCollection(db, "title_principals_namerating")
        titlesFx      <- setupTitleCollection(db, "enhancedNameRecords")
        autoTitlesFx  <- setupAutoTitleCollection(db, "autoTitleRecords")
        _ <- autoTitlesFx
          .insertMany(autosuggestTitleRecs)
          .attempt
          .flatMap:
            case Left(e) =>
              IO(L.error(s"failed to insert ${autosuggestTitleRecs.mkString}")) >> IO.raiseError(e)
            case Right(_) =>
              IO(L.info(s"succeeded inserts ${autosuggestTitleRecs.mkString}"))
        imdbQuery    = new ImdbQueryImpl[IO](peopleFx, peopleTitlesFx, titlesFx, autoTitlesFx, "localhost", mockHttpClient)
        params       = ReqParams()
        titlePrefix  = "Gone with the"
        resultStream = imdbQuery.getAutosuggestTitle(titlePrefix, None, 7.0, 0, params)
        results <- resultStream.compile.toList
      yield
        assert(results.nonEmpty)
        assert(results.forall(_.primaryTitle.startsWith("Gone with the")))

  test("getAutosuggestName should return suggestions based on name prefix"):
    withEmbeddedMongoClient: mongoclient =>
      for
        db            <- setupTestDatabase("test", mongoclient)
        peopleFx      <- setupAutoNameCollection(db, "autoNameRecords")
        peopleTitlesFx <- setupTitleCollection(db, "title_principals_namerating")
        titlesFx      <- setupTitleCollection(db, "nameRecords")
        autoTitlesFx  <- setupAutoTitleCollection(db, "autoTitleRecords")
        _ <- peopleFx
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

  def withEmbeddedMongoClient[A](test: MongoClient[IO] => IO[A]): Future[A] =
    withRunningEmbeddedMongo:
      MongoClient
        .fromConnectionString[IO](s"mongodb://localhost:$mongoPort")
        .use(test)
    .unsafeToFuture()(IORuntime.global)
