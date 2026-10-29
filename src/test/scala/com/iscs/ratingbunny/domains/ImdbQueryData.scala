package com.iscs.ratingbunny.domains

import mongo4cats.bson.{BsonValue, Document}
import mongo4cats.bson.syntax.*

object ImdbQueryData:
  val titlePaths: List[Some[String]] = List(
    Some("/9TGHDvWrqKBzwDxDodHYXEmOE6J.jpg"),
    Some("/t1wm4PgOQ8e4z1C6tk1yDNrps4T.jpg"),
    Some("/f89U3ADr1oiB1s9GkdPOEpXUk5H.jpg")
  )
  val titlePathRecs: List[Document] = List(
    Document(
      "_id" := "tt0234215",
      "tconst" := "tt0234215",
      "titleType" := "movie",
      "primaryTitle" := "The Matrix Reloaded",
      "originalTitle" := "The Matrix Reloaded",
      "primaryTitleLC" := "the matrix reloaded",
      "isAdult" := 0,
      "rated" := BsonValue.True,
      "startYear" := 2003d,
      "endYear" := 2005d,
      "runtimeMinutes" := 138d,
      "genres" := List("Action", "Sci-Fi"),
      "rating" := Document("average" := 7.2d, "votes" := 637431d)
    ),
    Document(
      "_id" := "tt0242653",
      "tconst" := "tt0242653",
      "titleType" := "movie",
      "primaryTitle" := "The Matrix Revolutions",
      "originalTitle" := "The Matrix Revolutions",
      "primaryTitleLC" := "the matrix revolutions",
      "isAdult" := 0,
      "rated" := BsonValue.True,
      "startYear" := 2003d,
      "endYear" := 2005d,
      "runtimeMinutes" := 129d,
      "genres" := List("Action", "Sci-Fi"),
      "rating" := Document("average" := 6.7d, "votes" := 548968d)
    ),
    Document(
      "_id" := "tt0133093",
      "tconst" := "tt0133093",
      "titleType" := "movie",
      "primaryTitle" := "The Matrix",
      "originalTitle" := "The Matrix",
      "primaryTitleLC" := "the matrix",
      "isAdult" := 0,
      "rated" := BsonValue.True,
      "startYear" := 1999d,
      "endYear" := 2005d,
      "runtimeMinutes" := 136d,
      "genres" := List("Action", "Sci-Fi"),
      "rating" := Document("average" := 8.7d, "votes" := 2083121d)
    )
  )

  val titleRecs: List[Document] = List(
    Document(
      "_id" := "tt0111161",
      "tconst" := "tt0111161",
      "titleType" := "movie",
      "primaryTitle" := "The Shawshank Redemption",
      "originalTitle" := "The Shawshank Redemption",
      "primaryTitleLC" := "the shawshank redemption",
      "isAdult" := 0,
      "rated" := BsonValue.True,
      "startYear" := 1994d,
      "endYear" := 2005d,
      "runtimeMinutes" := 142d,
      "genres" := List("Drama"),
      "rating" := Document("average" := 9.3d, "votes" := 2500000d)
    ),
    Document(
      "_id" := "tt0068646",
      "tconst" := "tt0068646",
      "titleType" := "movie",
      "primaryTitle" := "The Godfather",
      "originalTitle" := "The Godfather",
      "primaryTitleLC" := "the godfather",
      "isAdult" := 0,
      "rated" := BsonValue.True,
      "startYear" := 1972d,
      "endYear" := 2005d,
      "runtimeMinutes" := 175d,
      "genres" := List("Crime", "Drama"),
      "rating" := Document("average" := 9.2d, "votes" := 1700000d)
    )
  )
  val enhancedNameCompRecs: List[Document] = List(
    Document(
      "_id" := "tt2361317-16",
      "tconst" := "tt2361317",
      "nconst" := "nm0000138",
      "titleType" := "movie",
      "primaryTitle" := "Live by Night",
      "originalTitle" := "Live by Night",
      "primaryTitleLC" := "live by night",
      "isAdult" := 0,
      "rated" := BsonValue.True,
      "startYear" := 2016d,
      "runtimeMinutes" := 129d,
      "genres" := List("Action", "Crime", "Drama"),
      "rating" := Document("average" := 6.4d, "votes" := 60210d),
      "origCat" := "producer",
      "role" := "producer"
    ),
    Document(
      "_id" := "tt1663202-1",
      "tconst" := "tt1663202",
      "nconst" := "nm0000138",
      "titleType" := "movie",
      "primaryTitle" := "The Revenant",
      "originalTitle" := "The Revenant",
      "primaryTitleLC" := "the revenant",
      "isAdult" := 0,
      "rated" := BsonValue.True,
      "startYear" := 2015d,
      "runtimeMinutes" := 156d,
      "genres" := List("Action", "Adventure", "Drama"),
      "rating" := Document("average" := 8.0d, "votes" := 886424d),
      "characters" := List("Hugh Glass"),
      "origCat" := "actor",
      "role" := "actor"
    ),
    Document(
      "_id" := "tt4532826-16",
      "tconst" := "tt4532826",
      "nconst" := "nm0000138",
      "titleType" := "movie",
      "primaryTitle" := "Robin Hood",
      "originalTitle" := "Robin Hood",
      "primaryTitleLC" := "robin hood",
      "isAdult" := 0,
      "rated" := BsonValue.True,
      "startYear" := 2018d,
      "runtimeMinutes" := 116d,
      "genres" := List("Action", "Adventure", "Drama"),
      "rating" := Document("average" := 5.3d, "votes" := 80324d),
      "origCat" := "producer",
      "role" := "producer"
    )
  )

  val autosuggestTitleRecs: List[Document] = List(
    Document(
      "_id" := "tt27881481",
      "primaryTitle" := "Gone with the Boat",
      "primaryTitleLC" := "gone with the boat",
      "isAdult" := 0,
      "rated" := BsonValue.True,
      "startYear" := 2023d,
      "rating" := Document("average" := 7.5d, "votes" := 59d)
    ),
    Document(
      "_id" := "tt1111111",
      "primaryTitle" := "Gone with the Wind",
      "primaryTitleLC" := "gone with the wind",
      "isAdult" := 0,
      "rated" := BsonValue.True,
      "startYear" := 1939d,
      "rating" := Document("average" := 8.2d, "votes" := 813000d)
    )
  )

  val autosuggestNameRecs: List[Document] = List(
    Document(
      "_id" := "nm10937834",
      "primaryName" := "John Crane",
      "nameLC" := "john crane",
      "birthYear" := 1980,
      "primaryProfession" := List("self")
    ),
    Document(
      "_id" := "nm0186835",
      "primaryName" := "John Crawford",
      "nameLC" := "john crawford",
      "birthYear" := 1975,
      "primaryProfession" := List("cinematographer")
    )
  )
