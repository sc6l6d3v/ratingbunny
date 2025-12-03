# Autosuggest Endpoint Examples

Ready-to-run Postman/cURL examples for the autosuggest endpoints.
All requests expect JSON responses and most require authentication; include the
`Authorization: Bearer <token>` header unless noted otherwise.

## `/api/v3/autoname`
Retrieve people by name with optional year range filters.

**Request**
```bash
curl --location 'http://localhost:8084/api/v3/autoname?q=John%20Wa&lowyear=1950&highyear=1960' \
  --header 'Content-Type: application/json' \
  --header 'Authorization: Bearer "<token>"' \
  --header 'Cookie: __Host-nc_sameSiteCookielax=true; __Host-nc_sameSiteCookiestrict=true'
```

**Response (truncated)**
```json
[
  {
    "_id": "nm14574239",
    "primaryName": "John Waaben",
    "birthYear": null,
    "deathYear": null,
    "primaryProfession": [
      "camera_department",
      "production_department"
    ]
  },
  {
    "_id": "nm11729490",
    "primaryName": "John Waage",
    "birthYear": null,
    "deathYear": null,
    "primaryProfession": []
  }
]
```

## `/api/v3/autotitle`
Find titles by name with language, rating, vote and metadata filters.

**Request**
```bash
curl --location 'http://localhost:8084/api/v3/autotitle?q=Gone%20with%20the%20W&lang=en&rating=4.0&votes=30&genre=Drama&titletype=movie&isadult=0' \
  --header 'Content-Type: application/json' \
  --header 'Cookie: __Host-nc_sameSiteCookielax=true; __Host-nc_sameSiteCookiestrict=true'
```

**Response**
```json
[
  {
    "_id": "tt0031381",
    "primaryTitle": "Gone with the Wind",
    "startYear": 1939,
    "rating": {
      "average": 8.2,
      "votes": 352286.0
    }
  }
]
```

> None of the other image-related endpoints or security endpoints changed.
