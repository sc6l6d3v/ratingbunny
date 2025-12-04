package com.iscs.ratingbunny.domains

import mongo4cats.bson.Document
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
      "startYear" := 1972d,
      "endYear" := 2005d,
      "runtimeMinutes" := 175d,
      "genres" := List("Crime", "Drama"),
      "rating" := Document("average" := 9.2d, "votes" := 1700000d)
    )
  )
  val nameRecs: List[Document] = List(
    Document(
      "_id" := "tt4244998",
      "tconst" := "tt4244998",
      "titleType" := "movie",
      "primaryTitle" := "Alpha",
      "originalTitle" := "Alpha",
      "primaryTitleLC" := "alpha",
      "isAdult" := 0,
      "startYear" := 2018d,
      "endYear" := 2005d,
      "runtimeMinutes" := 96d,
      "genres" := List("Action", "Adventure", "Drama"),
      "rating" := Document("average" := 6.6d, "votes" := 67187d)
    ),
    Document(
      "_id" := "tt5523010",
      "tconst" := "tt5523010",
      "titleType" := "movie",
      "primaryTitle" := "The Nutcracker and the Four Realms",
      "originalTitle" := "The Nutcracker and the Four Realms",
      "primaryTitleLC" := "the nutcracker and the four realms",
      "isAdult" := 0,
      "startYear" := 2018d,
      "endYear" := 2005d,
      "runtimeMinutes" := 99d,
      "genres" := List("Adventure", "Family", "Fantasy"),
      "rating" := Document("average" := 5.6d, "votes" := 37761d)
    ),
    Document(
      "_id" := "tt5852632",
      "tconst" := "tt5852632",
      "titleType" := "movie",
      "primaryTitle" := "March of the Penguins 2: The Next Step",
      "originalTitle" := "L'empereur",
      "primaryTitleLC" := "march of the penguins 2: the next step",
      "isAdult" := 0,
      "startYear" := 2017d,
      "endYear" := 2005d,
      "runtimeMinutes" := 76d,
      "genres" := List("Adventure", "Comedy", "Documentary"),
      "rating" := Document("average" := 6.6d, "votes" := 868d)
    ),
    Document(
      "_id" := "tt3110958",
      "tconst" := "tt3110958",
      "titleType" := "movie",
      "primaryTitle" := "Now You See Me 2",
      "originalTitle" := "Now You See Me 2",
      "primaryTitleLC" := "now you see me 2",
      "isAdult" := 0,
      "startYear" := 2016d,
      "endYear" := 2005d,
      "runtimeMinutes" := 129d,
      "genres" := List("Action", "Adventure", "Comedy"),
      "rating" := Document("average" := 6.4d, "votes" := 326263d)
    ),
    Document(
      "_id" := "tt2638144",
      "tconst" := "tt2638144",
      "titleType" := "movie",
      "primaryTitle" := "Ben-Hur",
      "originalTitle" := "Ben-Hur",
      "primaryTitleLC" := "ben-hur",
      "isAdult" := 0,
      "startYear" := 2016d,
      "endYear" := 2005d,
      "runtimeMinutes" := 123d,
      "genres" := List("Action", "Adventure", "Drama"),
      "rating" := Document("average" := 5.7d, "votes" := 47227d)
    ),
    Document(
      "_id" := "tt1483013",
      "tconst" := "tt1483013",
      "titleType" := "movie",
      "primaryTitle" := "Oblivion",
      "originalTitle" := "Oblivion",
      "primaryTitleLC" := "oblivion",
      "isAdult" := 0,
      "startYear" := 2013d,
      "endYear" := 2005d,
      "runtimeMinutes" := 124d,
      "genres" := List("Action", "Adventure", "Sci-Fi"),
      "rating" := Document("average" := 7.0d, "votes" := 558257d)
    )
  )

  val nameCompRecs: List[Document] = List(
    Document(
      "_id" := "tt4244998-9",
      "tconst" := "tt4244998",
      "nconst" := "nm0000151",
      "titleType" := "movie",
      "primaryTitle" := "Alpha",
      "originalTitle" := "Alpha",
      "primaryTitleLC" := "alpha",
      "isAdult" := 0,
      "startYear" := 2018d,
      "runtimeMinutes" := 96d,
      "genres" := List("Action", "Adventure", "Drama"),
      "rating" := Document("average" := 6.6d, "votes" := 67198d),
      "characters" := List("Narrator"),
      "origCat" := "actor",
      "role" := "actor"
    ),
    Document(
      "_id" := "tt1245526-3",
      "tconst" := "tt1245526",
      "nconst" := "nm0000151",
      "titleType" := "movie",
      "primaryTitle" := "RED",
      "originalTitle" := "RED",
      "primaryTitleLC" := "red",
      "isAdult" := 0,
      "startYear" := 2010d,
      "runtimeMinutes" := 111d,
      "genres" := List("Action", "Comedy", "Crime"),
      "rating" := Document("average" := 7.0d, "votes" := 327181d),
      "characters" := List("Joe Matheson"),
      "origCat" := "actor",
      "role" := "actor"
    ),
    Document(
      "_id" := "tt1483013-2",
      "tconst" := "tt1483013",
      "nconst" := "nm0000151",
      "titleType" := "movie",
      "primaryTitle" := "Oblivion",
      "originalTitle" := "Oblivion",
      "primaryTitleLC" := "oblivion",
      "isAdult" := 0,
      "startYear" := 2013d,
      "runtimeMinutes" := 124d,
      "genres" := List("Action", "Adventure", "Sci-Fi"),
      "rating" := Document("average" := 7.0d, "votes" := 558329d),
      "characters" := List("Beech"),
      "origCat" := "actor",
      "role" := "actor"
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
      "startYear" := 2023d,
      "rating" := Document("average" := 7.5d, "votes" := 59d)
    ),
    Document(
      "_id" := "tt1111111",
      "primaryTitle" := "Gone with the Wind",
      "primaryTitleLC" := "gone with the wind",
      "startYear" := 1939d,
      "rating" := Document("average" := 8.2d, "votes" := 813000d)
    )
  )

  val autosuggestNameRecs: List[AutoNameRec] = List(
    AutoNameRec(
      _id = "nm10937834",
      primaryName = "John Crane",
      birthYear = Some(1980),
      primaryProfession = Some(List("self"))
    ),
    AutoNameRec(
      _id = "nm0186835",
      primaryName = "John Crawford",
      birthYear = Some(1975),
      primaryProfession = Some(List("cinematographer"))
    )
  )
