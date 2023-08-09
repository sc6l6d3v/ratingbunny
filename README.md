# Rating Slave

Working Scala REST backend to allow retrieval of movie info and posters.

### Docker

To run in production mode inside a Docker container we first have to build the image. E.g.

```
docker build -t nanothermite/ratingslave:rest .
```

The aforementioned command will build the image and tag it with the latest commit hash.

To run said image:

```
docker run -d -p 8080:8080 nanothermite/ratingslave:rest
```

To attach to said image via shell:

```
docker exec -it <imagehash> /bin/bash
```

REST syntax
GET http://localhost:8080/reldate/YYYY/MM/rating

where rating is a double ranging from 1 to 10.
