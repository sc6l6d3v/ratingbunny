package com.iscs.ratingbunny.domains

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

  val nameCompRecs: List[TitleRec] = List(
    TitleRec(
      _id = "tt4244998-9",
      tconst = Some("tt4244998"),
      nconst = Some("nm0000151"),
      titleType = Some("movie"),
      primaryTitle = Some("Alpha"),
      originalTitle = Some("Alpha"),
      isAdult = Some(0),
      startYear = Some(2018d),
      runtimeMinutes = Some(96d),
      genres = Some(List("Action", "Adventure", "Drama")),
      rating = Some(TitleRating(average = Some(6.6d), votes = Some(67198d))),
      characters = Some(List("Narrator")),
      origCat = Some("actor"),
      role = Some("actor")
    ),
    TitleRec(
      _id = "tt1245526-3",
      tconst = Some("tt1245526"),
      nconst = Some("nm0000151"),
      titleType = Some("movie"),
      primaryTitle = Some("RED"),
      originalTitle = Some("RED"),
      isAdult = Some(0),
      startYear = Some(2010d),
      runtimeMinutes = Some(111d),
      genres = Some(List("Action", "Comedy", "Crime")),
      rating = Some(TitleRating(average = Some(7.0d), votes = Some(327181d))),
      characters = Some(List("Joe Matheson")),
      origCat = Some("actor"),
      role = Some("actor")
    ),
    TitleRec(
      _id = "tt1483013-2",
      tconst = Some("tt1483013"),
      nconst = Some("nm0000151"),
      titleType = Some("movie"),
      primaryTitle = Some("Oblivion"),
      originalTitle = Some("Oblivion"),
      isAdult = Some(0),
      startYear = Some(2013d),
      runtimeMinutes = Some(124d),
      genres = Some(List("Action", "Adventure", "Sci-Fi")),
      rating = Some(TitleRating(average = Some(7.0d), votes = Some(558329d))),
      characters = Some(List("Beech")),
      origCat = Some("actor"),
      role = Some("actor")
    )
  )

  val enhancedNameCompRecs: List[TitleRec] = List(
    TitleRec(
      _id = "tt2361317-16",
      tconst = Some("tt2361317"),
      nconst = Some("nm0000138"),
      titleType = Some("movie"),
      primaryTitle = Some("Live by Night"),
      originalTitle = Some("Live by Night"),
      isAdult = Some(0),
      startYear = Some(2016d),
      runtimeMinutes = Some(129d),
      genres = Some(List("Action", "Crime", "Drama")),
      rating = Some(TitleRating(average = Some(6.4d), votes = Some(60210d))),
      origCat = Some("producer"),
      role = Some("producer")
    ),
    TitleRec(
      _id = "tt1663202-1",
      tconst = Some("tt1663202"),
      nconst = Some("nm0000138"),
      titleType = Some("movie"),
      primaryTitle = Some("The Revenant"),
      originalTitle = Some("The Revenant"),
      isAdult = Some(0),
      startYear = Some(2015d),
      runtimeMinutes = Some(156d),
      genres = Some(List("Action", "Adventure", "Drama")),
      rating = Some(TitleRating(average = Some(8.0d), votes = Some(886424d))),
      characters = Some(List("Hugh Glass")),
      origCat = Some("actor"),
      role = Some("actor")
    ),
    TitleRec(
      _id = "tt4532826-16",
      tconst = Some("tt4532826"),
      nconst = Some("nm0000138"),
      titleType = Some("movie"),
      primaryTitle = Some("Robin Hood"),
      originalTitle = Some("Robin Hood"),
      isAdult = Some(0),
      startYear = Some(2018d),
      runtimeMinutes = Some(116d),
      genres = Some(List("Action", "Adventure", "Drama")),
      rating = Some(TitleRating(average = Some(5.3d), votes = Some(80324d))),
      origCat = Some("producer"),
      role = Some("producer")
    )
  )

  val autosuggestTitleRecs: List[AutoTitleRec] = List(
    AutoTitleRec(
      _id = "tt27881481",
      primaryTitle = "Gone with the Boat",
      startYear = Some(2023d),
      rating = Some(TitleRating(average = Some(7.5d), votes = Some(59d)))
    ),
    AutoTitleRec(
      _id = "tt1111111",
      primaryTitle = "Gone with the Wind",
      startYear = Some(1939d),
      rating = Some(TitleRating(average = Some(8.2d), votes = Some(813000d)))
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
