# Release Scraper

Working Scala REST backend to allow scraping of release dates for movie info.

### Docker

To run in production mode inside a Docker container we first have to build the image. E.g.

```
docker build -t releasescraper:rest .
```

The aforementioned command will build the image and tag it with the latest commit hash.

To run said image:

```
docker run -d -p 8080:8080 releasescraper:rest
```

To attach to said image via shell:

```
docker exec -it <imagehash> /bin/bash
```

REST syntax
GET http://localhost:8080/reldate/YYYY/MM/rating

where rating is a double ranging from 1 to 10.
