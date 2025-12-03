# Search Endpoint Examples (Name & Title)

Ready-to-run Postman/cURL examples for the core search endpoints that return full records.
All requests expect JSON responses and most require authentication; include the
`Authorization: Bearer <token>` header unless noted otherwise.

## `/api/v3/pro/name/{page}/{pageSize}` (actor search)
Returns roles for a given person (`nconst`) with optional genre, year, and vote filters.

**Request**
```bash
curl --location 'http://localhost:8084/api/v3/pro/name/1/4?ws=600&wh=800&cs=211&ch=328&off=0' \
  --header 'Content-Type: application/json' \
  --header 'Authorization: Bearer <token>' \
  --header 'Cookie: __Host-nc_sameSiteCookielax=true; __Host-nc_sameSiteCookiestrict=true' \
  --data '{
    "query": "nm0001478",
    "genre": [
      "Comedy"
    ],
    "year": [
      1937,
      1979
    ],
    "votes": 0
  }'
```

**Response (truncated)**
```json
[
  {
    "_id": "tt0039230",
    "tconst": "tt0039230",
    "nconst": "nm0001478",
    "titleType": "movie",
    "primaryTitle": "Bury Me Dead",
    "originalTitle": "Bury Me Dead",
    "isAdult": null,
    "startYear": 1947.0,
    "endYear": null,
    "runtimeMinutes": null,
    "genres": [
      "Comedy",
      "Crime",
      "Drama"
    ],
    "rating": {
      "average": 5.8,
      "votes": 447.0
    },
    "hasUS": true,
    "hasEN": null,
    "langs": [
      "en",
      "ja"
    ],
    "langMask": 3,
    "usBoost": 13.0,
    "characters": [
      "Barbara Carlin]"
    ],
    "origCat": "actress",
    "role": "cast"
  }
]
```

## `/api/v3/title/{page}/{pageSize}`
Search for titles with either exact matching or regex-based search types.

### Exact search
**Request**
```bash
curl --location 'http://localhost:8084/api/v3/title/1/5?ws=936&wh=1404&cs=230&ch=345&off=0' \
  --header 'Content-Type: application/json' \
  --header 'Authorization: Bearer <token>' \
  --header 'Cookie: __Host-nc_sameSiteCookielax=true; __Host-nc_sameSiteCookiestrict=true' \
  --data '{
    "query": "Tenet",
    "year": [
      2020,
      2021
    ],
    "titleType": [
      "movie"
    ],
    "isAdult": false,
    "searchType": "exact"
  }'
```

**Response**
```json
[
  {
    "_id": "tt6723592",
    "averageRating": 7.3,
    "numVotes": 645271,
    "titleType": "movie",
    "primaryTitle": "Tenet",
    "originalTitle": "Tenet",
    "isAdult": 0,
    "startYear": 2020,
    "endYear": 0,
    "runtimeMinutes": 150,
    "genresList": [
      "Action",
      "Sci-Fi",
      "Thriller"
    ]
  }
]
```

### Regex search
**Request**
```bash
curl --location 'http://localhost:8084/api/v3/title/1/5?ws=936&wh=1404&cs=230&ch=345&off=0' \
  --header 'Content-Type: application/json' \
  --header 'Authorization: Bearer <token>' \
  --header 'Cookie: __Host-nc_sameSiteCookielax=true; __Host-nc_sameSiteCookiestrict=true' \
  --data '{
    "genre": [
      "Drama"
    ],
    "year": [
      1939,
      1956
    ],
    "votes": 50,
    "titleType": [
      "movie"
    ],
    "isAdult": false,
    "query": "Gone ",
    "searchType": "regex"
  }'
```

**Response (truncated)**
```json
[
  {
    "_id": "tt0031381",
    "averageRating": 8.2,
    "numVotes": 351051,
    "titleType": "movie",
    "primaryTitle": "Gone with the Wind",
    "originalTitle": "Gone with the Wind",
    "isAdult": 0,
    "startYear": 1939,
    "endYear": 0,
    "runtimeMinutes": 238,
    "genresList": [
      "Drama",
      "Romance",
      "War"
    ]
  }
]
```
