package com.iscs.ratingbunny.domains

import mongo4cats.bson.{BsonValue, Document, ObjectId}
import mongo4cats.bson.syntax.*

object ImdbQueryData {
  val titlePaths: List[Some[String]] = List(
    Some("/9TGHDvWrqKBzwDxDodHYXEmOE6J.jpg"),
    Some("/t1wm4PgOQ8e4z1C6tk1yDNrps4T.jpg"),
    Some("/f89U3ADr1oiB1s9GkdPOEpXUk5H.jpg")
  )
  val titlePathRecs: List[TitleRec] = List(
    TitleRec(
      _id = "tt0234215",
      averageRating = Some(7.2d),
      numVotes = Some(637431),
      titleType = "movie",
      primaryTitle = "The Matrix Reloaded",
      originalTitle = "The Matrix Reloaded",
      isAdult = 0,
      startYear = 2003,
      endYear = 2005,
      runtimeMinutes = Some(138),
      genresList = Some(List("Action", "Sci-Fi"))
//        posterPath = Some("/9TGHDvWrqKBzwDxDodHYXEmOE6J.jpg")
    ),
    TitleRec(
      _id = "tt0242653",
      averageRating = Some(6.7d),
      numVotes = Some(548968),
      titleType = "movie",
      primaryTitle = "The Matrix Revolutions",
      originalTitle = "The Matrix Revolutions",
      isAdult = 0,
      startYear = 2003,
      endYear = 2005,
      runtimeMinutes = Some(129),
      genresList = Some(List("Action", "Sci-Fi"))
//      posterPath = Some("/t1wm4PgOQ8e4z1C6tk1yDNrps4T.jpg")
    ),
    TitleRec(
      _id = "tt0133093",
      averageRating = Some(8.7),
      numVotes = Some(2083121),
      titleType = "movie",
      primaryTitle = "The Matrix",
      originalTitle = "The Matrix",
      isAdult = 0,
      startYear = 1999,
      endYear = 2005,
      runtimeMinutes = Some(136),
      genresList = Some(List("Action", "Sci-Fi"))
//      posterPath = Some("/f89U3ADr1oiB1s9GkdPOEpXUk5H.jpg")
    )
  )

  val titleRecs: List[TitleRec] = List(
    TitleRec(
      _id = "tt0111161",
      averageRating = Some(9.3),
      numVotes = Some(2500000),
      titleType = "movie",
      primaryTitle = "The Shawshank Redemption",
      originalTitle = "The Shawshank Redemption",
      isAdult = 0,
      startYear = 1994,
      endYear = 2005,
      runtimeMinutes = Some(142),
      genresList = Some(List("Drama"))
    ),
    TitleRec(
      _id = "tt0068646",
      averageRating = Some(9.2),
      numVotes = Some(1700000),
      titleType = "movie",
      primaryTitle = "The Godfather",
      originalTitle = "The Godfather",
      isAdult = 0,
      startYear = 1972,
      endYear = 2005,
      runtimeMinutes = Some(175),
      genresList = Some(List("Crime", "Drama"))
    )
  )
  val nameRecs: List[TitleRec] = List(
    TitleRec(
      _id = "tt4244998",
      averageRating = Some(6.6),
      numVotes = Some(67187),
      titleType = "movie",
      primaryTitle = "Alpha",
      originalTitle = "Alpha",
      isAdult = 0,
      startYear = 2018,
      endYear = 2005,
      runtimeMinutes = Some(96),
      genresList = Some(
        List(
          "Action",
          "Adventure",
          "Drama"
        )
      )
    ),
    TitleRec(
      _id = "tt5523010",
      averageRating = Some(5.6),
      numVotes = Some(37761),
      titleType = "movie",
      primaryTitle = "The Nutcracker and the Four Realms",
      originalTitle = "The Nutcracker and the Four Realms",
      isAdult = 0,
      startYear = 2018,
      endYear = 2005,
      runtimeMinutes = Some(99),
      genresList = Some(
        List(
          "Adventure",
          "Family",
          "Fantasy"
        )
      )
    ),
    TitleRec(
      _id = "tt5852632",
      averageRating = Some(6.6),
      numVotes = Some(868),
      titleType = "movie",
      primaryTitle = "March of the Penguins 2: The Next Step",
      originalTitle = "L'empereur",
      isAdult = 0,
      startYear = 2017,
      endYear = 2005,
      runtimeMinutes = Some(76),
      genresList = Some(
        List(
          "Adventure",
          "Comedy",
          "Documentary"
        )
      )
    ),
    TitleRec(
      _id = "tt3110958",
      averageRating = Some(6.4),
      numVotes = Some(326263),
      titleType = "movie",
      primaryTitle = "Now You See Me 2",
      originalTitle = "Now You See Me 2",
      isAdult = 0,
      startYear = 2016,
      endYear = 2005,
      runtimeMinutes = Some(129),
      genresList = Some(
        List(
          "Action",
          "Adventure",
          "Comedy"
        )
      )
    ),
    TitleRec(
      _id = "tt2638144",
      averageRating = Some(5.7),
      numVotes = Some(47227),
      titleType = "movie",
      primaryTitle = "Ben-Hur",
      originalTitle = "Ben-Hur",
      isAdult = 0,
      startYear = 2016,
      endYear = 2005,
      runtimeMinutes = Some(123),
      genresList = Some(
        List(
          "Action",
          "Adventure",
          "Drama"
        )
      )
    ),
    TitleRec(
      _id = "tt1483013",
      averageRating = Some(7.0),
      numVotes = Some(558257),
      titleType = "movie",
      primaryTitle = "Oblivion",
      originalTitle = "Oblivion",
      isAdult = 0,
      startYear = 2013,
      endYear = 2005,
      runtimeMinutes = Some(124),
      genresList = Some(
        List(
          "Action",
          "Adventure",
          "Sci-Fi"
        )
      )
    )
  )

  val nameCompRecs: List[Document] = List(
    Document(
      "_id"         := "tt3181776-3",
      "primaryName" := "Morgan Freeman",
      "tconst"      := "tt3181776",
      "ordering"    := 3,
      "nconst"      := "nm0000151",
      "category"    := "actor",
      "job"         := "\\N",
      "characters"  := "[\"Senator\"]",
      "firstName"   := "Morgan",
      "lastName"    := "Freeman",
      "startYear"   := 2015,
      "endYear"     := 0,
      "genresList" := List(
        "Action",
        "Crime",
        "Thriller"
      ),
      "isAdult"        := 0,
      "originalTitle"  := "Momentum",
      "primaryTitle"   := "Momentum",
      "runtimeMinutes" := 96,
      "titleType"      := "movie",
      "averageRating"  := 5.5,
      "numVotes"       := 17070
    ),
    Document(
      "_id"         := "tt4244998-9",
      "primaryName" := "Morgan Freeman",
      "tconst"      := "tt4244998",
      "ordering"    := 9,
      "nconst"      := "nm0000151",
      "category"    := "actor",
      "job"         := "\\N",
      "characters"  := "[\"Narrator\"]",
      "firstName"   := "Morgan",
      "lastName"    := "Freeman",
      "startYear"   := 2018,
      "endYear"     := 0,
      "genresList" := List(
        "Action",
        "Adventure",
        "Drama"
      ),
      "isAdult"        := 0,
      "originalTitle"  := "Alpha",
      "primaryTitle"   := "Alpha",
      "runtimeMinutes" := 96,
      "titleType"      := "movie",
      "averageRating"  := 6.6,
      "numVotes"       := 67198
    ),
    Document(
      "_id"         := "tt2493486-2",
      "primaryName" := "Morgan Freeman",
      "tconst"      := "tt2493486",
      "ordering"    := 2,
      "nconst"      := "nm0000151",
      "category"    := "actor",
      "job"         := "\\N",
      "characters"  := "[\"Bartok\"]",
      "firstName"   := "Morgan",
      "lastName"    := "Freeman",
      "startYear"   := 2015,
      "endYear"     := 0,
      "genresList" := List(
        "Action",
        "Drama",
        "History"
      ),
      "isAdult"        := 0,
      "originalTitle"  := "Last Knights",
      "primaryTitle"   := "Last Knights",
      "runtimeMinutes" := 115,
      "titleType"      := "movie",
      "averageRating"  := 6.2,
      "numVotes"       := 46098
    ),
    Document(
      "_id"         := "tt3300542-3",
      "primaryName" := "Morgan Freeman",
      "tconst"      := "tt3300542",
      "ordering"    := 3,
      "nconst"      := "nm0000151",
      "category"    := "actor",
      "job"         := "\\N",
      "characters"  := "[\"VP Allan Trumbull\"]",
      "firstName"   := "Morgan",
      "lastName"    := "Freeman",
      "startYear"   := 2016,
      "endYear"     := 0,
      "genresList" := List(
        "Action",
        "Thriller"
      ),
      "isAdult"        := 0,
      "originalTitle"  := "London Has Fallen",
      "primaryTitle"   := "London Has Fallen",
      "runtimeMinutes" := 99,
      "titleType"      := "movie",
      "averageRating"  := 5.9,
      "numVotes"       := 171557
    ),
    Document(
      "_id"         := "tt2209764-3",
      "primaryName" := "Morgan Freeman",
      "tconst"      := "tt2209764",
      "ordering"    := 3,
      "nconst"      := "nm0000151",
      "category"    := "actor",
      "job"         := "\\N",
      "characters"  := "[\"Joseph Tagger\"]",
      "firstName"   := "Morgan",
      "lastName"    := "Freeman",
      "startYear"   := 2014,
      "endYear"     := 0,
      "genresList" := List(
        "Action",
        "Drama",
        "Sci-Fi"
      ),
      "isAdult"        := 0,
      "originalTitle"  := "Transcendence",
      "primaryTitle"   := "Transcendence",
      "runtimeMinutes" := 119,
      "titleType"      := "movie",
      "averageRating"  := 6.2,
      "numVotes"       := 240132
    ),
    Document(
      "_id"         := "tt1345836-8",
      "primaryName" := "Morgan Freeman",
      "tconst"      := "tt1345836",
      "ordering"    := 8,
      "nconst"      := "nm0000151",
      "category"    := "actor",
      "job"         := "\\N",
      "characters"  := "[\"Fox\"]",
      "firstName"   := "Morgan",
      "lastName"    := "Freeman",
      "startYear"   := 2012,
      "endYear"     := 0,
      "genresList" := List(
        "Action",
        "Drama",
        "Thriller"
      ),
      "isAdult"        := 0,
      "originalTitle"  := "The Dark Knight Rises",
      "primaryTitle"   := "The Dark Knight Rises",
      "runtimeMinutes" := 164,
      "titleType"      := "movie",
      "averageRating"  := 8.4,
      "numVotes"       := 1852423
    ),
    Document(
      "_id"         := "tt1245526-3",
      "primaryName" := "Morgan Freeman",
      "tconst"      := "tt1245526",
      "ordering"    := 3,
      "nconst"      := "nm0000151",
      "category"    := "actor",
      "job"         := "\\N",
      "characters"  := "[\"Joe Matheson\"]",
      "firstName"   := "Morgan",
      "lastName"    := "Freeman",
      "startYear"   := 2010,
      "endYear"     := 0,
      "genresList" := List(
        "Action",
        "Comedy",
        "Crime"
      ),
      "isAdult"        := 0,
      "originalTitle"  := "RED",
      "primaryTitle"   := "RED",
      "runtimeMinutes" := 111,
      "titleType"      := "movie",
      "averageRating"  := 7.0,
      "numVotes"       := 327181
    ),
    Document(
      "_id"         := "tt2638144-8",
      "primaryName" := "Morgan Freeman",
      "tconst"      := "tt2638144",
      "ordering"    := 8,
      "nconst"      := "nm0000151",
      "category"    := "actor",
      "job"         := "\\N",
      "characters"  := "[\"Ilderim\"]",
      "firstName"   := "Morgan",
      "lastName"    := "Freeman",
      "startYear"   := 2016,
      "endYear"     := 0,
      "genresList" := List(
        "Action",
        "Adventure",
        "Drama"
      ),
      "isAdult"        := 0,
      "originalTitle"  := "Ben-Hur",
      "primaryTitle"   := "Ben-Hur",
      "runtimeMinutes" := 123,
      "titleType"      := "movie",
      "averageRating"  := 5.7,
      "numVotes"       := 47229
    ),
    Document(
      "_id"         := "tt3110958-11",
      "primaryName" := "Morgan Freeman",
      "tconst"      := "tt3110958",
      "ordering"    := 11,
      "nconst"      := "nm0000151",
      "category"    := "actor",
      "job"         := "\\N",
      "characters"  := "[\"Thaddeus Bradley\"]",
      "firstName"   := "Morgan",
      "lastName"    := "Freeman",
      "startYear"   := 2016,
      "endYear"     := 0,
      "genresList" := List(
        "Action",
        "Adventure",
        "Comedy"
      ),
      "isAdult"        := 0,
      "originalTitle"  := "Now You See Me 2",
      "primaryTitle"   := "Now You See Me 2",
      "runtimeMinutes" := 129,
      "titleType"      := "movie",
      "averageRating"  := 6.4,
      "numVotes"       := 326304
    ),
    Document(
      "_id"         := "tt2302755-3",
      "primaryName" := "Morgan Freeman",
      "tconst"      := "tt2302755",
      "ordering"    := 3,
      "nconst"      := "nm0000151",
      "category"    := "actor",
      "job"         := "\\N",
      "characters"  := "[\"Speaker Trumbull\"]",
      "firstName"   := "Morgan",
      "lastName"    := "Freeman",
      "startYear"   := 2013,
      "endYear"     := 0,
      "genresList" := List(
        "Action",
        "Thriller"
      ),
      "isAdult"        := 0,
      "originalTitle"  := "Olympus Has Fallen",
      "primaryTitle"   := "Olympus Has Fallen",
      "runtimeMinutes" := 119,
      "titleType"      := "movie",
      "averageRating"  := 6.5,
      "numVotes"       := 294977
    ),
    Document(
      "_id"         := "tt1483013-2",
      "primaryName" := "Morgan Freeman",
      "tconst"      := "tt1483013",
      "ordering"    := 2,
      "nconst"      := "nm0000151",
      "category"    := "actor",
      "job"         := "\\N",
      "characters"  := "[\"Beech\"]",
      "firstName"   := "Morgan",
      "lastName"    := "Freeman",
      "startYear"   := 2013,
      "endYear"     := 0,
      "genresList" := List(
        "Action",
        "Adventure",
        "Sci-Fi"
      ),
      "isAdult"        := 0,
      "originalTitle"  := "Oblivion",
      "primaryTitle"   := "Oblivion",
      "runtimeMinutes" := 124,
      "titleType"      := "movie",
      "averageRating"  := 7.0,
      "numVotes"       := 558329
    ),
    Document(
      "_id"         := "tt2872732-2",
      "primaryName" := "Morgan Freeman",
      "tconst"      := "tt2872732",
      "ordering"    := 2,
      "nconst"      := "nm0000151",
      "category"    := "actor",
      "job"         := "\\N",
      "characters"  := "[\"Professor Norman\"]",
      "firstName"   := "Morgan",
      "lastName"    := "Freeman",
      "startYear"   := 2014,
      "endYear"     := 0,
      "genresList" := List(
        "Action",
        "Sci-Fi",
        "Thriller"
      ),
      "isAdult"        := 0,
      "originalTitle"  := "Lucy",
      "primaryTitle"   := "Lucy",
      "runtimeMinutes" := 89,
      "titleType"      := "movie",
      "averageRating"  := 6.4,
      "numVotes"       := 541568
    ),
    Document(
      "_id"         := "tt6189022-10",
      "primaryName" := "Morgan Freeman",
      "tconst"      := "tt6189022",
      "ordering"    := 10,
      "nconst"      := "nm0000151",
      "category"    := "actor",
      "job"         := "\\N",
      "characters"  := "[\"President Trumbull\"]",
      "firstName"   := "Morgan",
      "lastName"    := "Freeman",
      "startYear"   := 2019,
      "endYear"     := 0,
      "genresList" := List(
        "Action",
        "Thriller"
      ),
      "isAdult"        := 0,
      "originalTitle"  := "Angel Has Fallen",
      "primaryTitle"   := "Angel Has Fallen",
      "runtimeMinutes" := 121,
      "titleType"      := "movie",
      "averageRating"  := 6.4,
      "numVotes"       := 112591
    )
  )

  val enhancedNameCompRecs: List[Document] = List(
    Document(
      "_id"         := "tt2361317-16",
      "primaryName" := "Leonardo DiCaprio",
      "tconst"      := "tt2361317",
      "ordering"    := 16,
      "nconst"      := "nm0000138",
      "category"    := "producer",
      "job"         := "producer",
      "characters"  := "\\N",
      "firstName"   := "Leonardo",
      "lastName"    := "DiCaprio",
      "startYear"   := 2016,
      "endYear"     := 0,
      "genresList" := List(
        "Action",
        "Crime",
        "Drama"
      ),
      "isAdult"        := 0,
      "originalTitle"  := "Live by Night",
      "primaryTitle"   := "Live by Night",
      "runtimeMinutes" := 129,
      "titleType"      := "movie",
      "averageRating"  := 6.4,
      "numVotes"       := 60210
    ),
    Document(
      "_id"         := "tt1663202-1",
      "primaryName" := "Leonardo DiCaprio",
      "tconst"      := "tt1663202",
      "ordering"    := 1,
      "nconst"      := "nm0000138",
      "category"    := "actor",
      "job"         := "\\N",
      "characters"  := "List(\"Hugh Glass\"]",
      "firstName"   := "Leonardo",
      "lastName"    := "DiCaprio",
      "startYear"   := 2015,
      "endYear"     := 0,
      "genresList" := List(
        "Action",
        "Adventure",
        "Drama"
      ),
      "isAdult"        := 0,
      "originalTitle"  := "The Revenant",
      "primaryTitle"   := "The Revenant",
      "runtimeMinutes" := 156,
      "titleType"      := "movie",
      "averageRating"  := 8.0,
      "numVotes"       := 886424
    ),
    Document(
      "_id"         := "tt4532826-16",
      "primaryName" := "Leonardo DiCaprio",
      "tconst"      := "tt4532826",
      "ordering"    := 16,
      "nconst"      := "nm0000138",
      "category"    := "producer",
      "job"         := "producer",
      "characters"  := "\\N",
      "firstName"   := "Leonardo",
      "lastName"    := "DiCaprio",
      "startYear"   := 2018,
      "endYear"     := 0,
      "genresList" := List(
        "Action",
        "Adventure",
        "Drama"
      ),
      "isAdult"        := 0,
      "originalTitle"  := "Robin Hood",
      "primaryTitle"   := "Robin Hood",
      "runtimeMinutes" := 116,
      "titleType"      := "movie",
      "averageRating"  := 5.3,
      "numVotes"       := 80324
    )
  )

  val autosuggestTitleRecs: List[Document] = List(
    Document(
      "_id"         := "tt27881481-1",
      "primaryName" := "Zhaomei Ge",
      "tconst"      := "tt27881481",
      "ordering"    := 1,
      "nconst"      := "nm7750049",
      "category"    := "actress",
      "job"         := "\\N",
      "characters"  := "[\"Zhou Jin\"]",
      "firstName"   := "Zhaomei",
      "lastName"    := "Ge",
      "startYear"   := 2023,
      "endYear"     := 0,
      "genresList" := List(
        "Drama",
        "Family"
      ),
      "isAdult"        := 0,
      "originalTitle"  := "Cheng chuan er qu",
      "primaryTitle"   := "Gone with the Boat",
      "runtimeMinutes" := 99,
      "titleType"      := "movie",
      "averageRating"  := 7.5,
      "numVotes"       := 59
    ),
    Document(
      "_id"         := "tt27881481-2",
      "primaryName" := "Dan Liu",
      "tconst"      := "tt27881481",
      "ordering"    := 2,
      "nconst"      := "nm2727871",
      "category"    := "actress",
      "job"         := "\\N",
      "characters"  := "[\"Su Nianzhen\"]",
      "firstName"   := "Dan",
      "lastName"    := "Liu",
      "startYear"   := 2023,
      "endYear"     := 0,
      "genresList" := List(
        "Drama",
        "Family"
      ),
      "isAdult"        := 0,
      "originalTitle"  := "Cheng chuan er qu",
      "primaryTitle"   := "Gone with the Boat",
      "runtimeMinutes" := 99,
      "titleType"      := "movie",
      "averageRating"  := 7.5,
      "numVotes"       := 59
    ),
    Document(
      "_id"         := "tt27881481-3",
      "primaryName" := "Zhoukai Wu",
      "tconst"      := "tt27881481",
      "ordering"    := 3,
      "nconst"      := "nm7939417",
      "category"    := "actor",
      "job"         := "\\N",
      "characters"  := "[\"Su Nianqing\"]",
      "firstName"   := "Zhoukai",
      "lastName"    := "Wu",
      "startYear"   := 2023,
      "endYear"     := 0,
      "genresList" := List(
        "Drama",
        "Family"
      ),
      "isAdult"        := 0,
      "originalTitle"  := "Cheng chuan er qu",
      "primaryTitle"   := "Gone with the Boat",
      "runtimeMinutes" := 99,
      "titleType"      := "movie",
      "averageRating"  := 7.5,
      "numVotes"       := 59
    )
  )

  val autosuggestNameRecs: List[Document] = List(
    Document(
      "_id"         := "tt9351980-8",
      "primaryName" := "John Crane",
      "tconst"      := "tt9351980",
      "ordering"    := 8,
      "nconst"      := "nm10937834",
      "category"    := "self",
      "job"         := "\\N",
      "characters"  := "[\"Self - Fuyao Safety Director\"]",
      "firstName"   := "John",
      "lastName"    := "Crane",
      "startYear"   := 2019,
      "endYear"     := 0,
      "genresList" := List(
        "Documentary"
      ),
      "isAdult"        := 0,
      "originalTitle"  := "American Factory",
      "primaryTitle"   := "American Factory",
      "runtimeMinutes" := 110,
      "titleType"      := "movie",
      "averageRating"  := 7.4,
      "numVotes"       := 24093
    ),
    Document(
      "_id"         := "tt3884528-14",
      "primaryName" := "John Crane",
      "tconst"      := "tt3884528",
      "ordering"    := 14,
      "nconst"      := "nm6945864",
      "category"    := "writer",
      "job"         := "story consultant: Original Batkid Day Event footage",
      "characters"  := "\\N",
      "firstName"   := "John",
      "lastName"    := "Crane",
      "startYear"   := 2015,
      "endYear"     := 0,
      "genresList" := List(
        "Biography",
        "Documentary",
        "Family"
      ),
      "isAdult"        := 0,
      "originalTitle"  := "Batkid Begins",
      "primaryTitle"   := "Batkid Begins: The Wish Heard Around the World",
      "runtimeMinutes" := 87,
      "titleType"      := "movie",
      "averageRating"  := 7.1,
      "numVotes"       := 1790
    ),
    Document(
      "_id"         := "tt0266519-15",
      "primaryName" := "John Crawford",
      "tconst"      := "tt0266519",
      "ordering"    := 15,
      "nconst"      := "nm0186835",
      "category"    := "cinematographer",
      "job"         := "\\N",
      "characters"  := "\\N",
      "firstName"   := "John",
      "lastName"    := "Crawford",
      "startYear"   := 2000,
      "endYear"     := 0,
      "genresList" := List(
        "Comedy",
        "Romance"
      ),
      "isAdult"        := 0,
      "originalTitle"  := "Everything for a Reason",
      "primaryTitle"   := "Everything for a Reason",
      "runtimeMinutes" := 89,
      "titleType"      := "movie",
      "averageRating"  := 6.2,
      "numVotes"       := 1554
    )
  )
}
