package com.iscs.ratingbunny.domains

import mongo4cats.bson.{BsonValue, Document, ObjectId}
import mongo4cats.bson.syntax.*

object ImdbQueryData:
  val titlePaths: List[Some[String]] = List(
    Some("/9TGHDvWrqKBzwDxDodHYXEmOE6J.jpg"),
    Some("/t1wm4PgOQ8e4z1C6tk1yDNrps4T.jpg"),
    Some("/f89U3ADr1oiB1s9GkdPOEpXUk5H.jpg")
  )
  val titlePathRecs: List[TitleRec] = List(
    TitleRec(
      _id = "tt0234215",
      tconst = Some("tt0234215"),
      titleType = Some("movie"),
      primaryTitle = Some("The Matrix Reloaded"),
      originalTitle = Some("The Matrix Reloaded"),
      isAdult = Some(0),
      startYear = Some(2003d),
      endYear = Some(2005d),
      runtimeMinutes = Some(138d),
      genres = Some(List("Action", "Sci-Fi")),
      rating = Some(TitleRating(average = Some(7.2d), votes = Some(637431d)))
    ),
    TitleRec(
      _id = "tt0242653",
      tconst = Some("tt0242653"),
      titleType = Some("movie"),
      primaryTitle = Some("The Matrix Revolutions"),
      originalTitle = Some("The Matrix Revolutions"),
      isAdult = Some(0),
      startYear = Some(2003d),
      endYear = Some(2005d),
      runtimeMinutes = Some(129d),
      genres = Some(List("Action", "Sci-Fi")),
      rating = Some(TitleRating(average = Some(6.7d), votes = Some(548968d)))
    ),
    TitleRec(
      _id = "tt0133093",
      tconst = Some("tt0133093"),
      titleType = Some("movie"),
      primaryTitle = Some("The Matrix"),
      originalTitle = Some("The Matrix"),
      isAdult = Some(0),
      startYear = Some(1999d),
      endYear = Some(2005d),
      runtimeMinutes = Some(136d),
      genres = Some(List("Action", "Sci-Fi")),
      rating = Some(TitleRating(average = Some(8.7d), votes = Some(2083121d)))
    )
  )

  val titleRecs: List[TitleRec] = List(
    TitleRec(
      _id = "tt0111161",
      tconst = Some("tt0111161"),
      titleType = Some("movie"),
      primaryTitle = Some("The Shawshank Redemption"),
      originalTitle = Some("The Shawshank Redemption"),
      isAdult = Some(0),
      startYear = Some(1994d),
      endYear = Some(2005d),
      runtimeMinutes = Some(142d),
      genres = Some(List("Drama")),
      rating = Some(TitleRating(average = Some(9.3d), votes = Some(2500000d)))
    ),
    TitleRec(
      _id = "tt0068646",
      tconst = Some("tt0068646"),
      titleType = Some("movie"),
      primaryTitle = Some("The Godfather"),
      originalTitle = Some("The Godfather"),
      isAdult = Some(0),
      startYear = Some(1972d),
      endYear = Some(2005d),
      runtimeMinutes = Some(175d),
      genres = Some(List("Crime", "Drama")),
      rating = Some(TitleRating(average = Some(9.2d), votes = Some(1700000d)))
    )
  )
  val nameRecs: List[TitleRec] = List(
    TitleRec(
      _id = "tt4244998",
      tconst = Some("tt4244998"),
      titleType = Some("movie"),
      primaryTitle = Some("Alpha"),
      originalTitle = Some("Alpha"),
      isAdult = Some(0),
      startYear = Some(2018d),
      endYear = Some(2005d),
      runtimeMinutes = Some(96d),
      genres = Some(
        List(
          "Action",
          "Adventure",
          "Drama"
        )
      ),
      rating = Some(TitleRating(average = Some(6.6d), votes = Some(67187d)))
    ),
    TitleRec(
      _id = "tt5523010",
      tconst = Some("tt5523010"),
      titleType = Some("movie"),
      primaryTitle = Some("The Nutcracker and the Four Realms"),
      originalTitle = Some("The Nutcracker and the Four Realms"),
      isAdult = Some(0),
      startYear = Some(2018d),
      endYear = Some(2005d),
      runtimeMinutes = Some(99d),
      genres = Some(
        List(
          "Adventure",
          "Family",
          "Fantasy"
        )
      ),
      rating = Some(TitleRating(average = Some(5.6d), votes = Some(37761d)))
    ),
    TitleRec(
      _id = "tt5852632",
      tconst = Some("tt5852632"),
      titleType = Some("movie"),
      primaryTitle = Some("March of the Penguins 2: The Next Step"),
      originalTitle = Some("L'empereur"),
      isAdult = Some(0),
      startYear = Some(2017d),
      endYear = Some(2005d),
      runtimeMinutes = Some(76d),
      genres = Some(
        List(
          "Adventure",
          "Comedy",
          "Documentary"
        )
      ),
      rating = Some(TitleRating(average = Some(6.6d), votes = Some(868d)))
    ),
    TitleRec(
      _id = "tt3110958",
      tconst = Some("tt3110958"),
      titleType = Some("movie"),
      primaryTitle = Some("Now You See Me 2"),
      originalTitle = Some("Now You See Me 2"),
      isAdult = Some(0),
      startYear = Some(2016d),
      endYear = Some(2005d),
      runtimeMinutes = Some(129d),
      genres = Some(
        List(
          "Action",
          "Adventure",
          "Comedy"
        )
      ),
      rating = Some(TitleRating(average = Some(6.4d), votes = Some(326263d)))
    ),
    TitleRec(
      _id = "tt2638144",
      tconst = Some("tt2638144"),
      titleType = Some("movie"),
      primaryTitle = Some("Ben-Hur"),
      originalTitle = Some("Ben-Hur"),
      isAdult = Some(0),
      startYear = Some(2016d),
      endYear = Some(2005d),
      runtimeMinutes = Some(123d),
      genres = Some(
        List(
          "Action",
          "Adventure",
          "Drama"
        )
      ),
      rating = Some(TitleRating(average = Some(5.7d), votes = Some(47227d)))
    ),
    TitleRec(
      _id = "tt1483013",
      tconst = Some("tt1483013"),
      titleType = Some("movie"),
      primaryTitle = Some("Oblivion"),
      originalTitle = Some("Oblivion"),
      isAdult = Some(0),
      startYear = Some(2013d),
      endYear = Some(2005d),
      runtimeMinutes = Some(124d),
      genres = Some(
        List(
          "Action",
          "Adventure",
          "Sci-Fi"
        )
      ),
      rating = Some(TitleRating(average = Some(7.0d), votes = Some(558257d)))
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
