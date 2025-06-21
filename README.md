# Rating Slave

Working Scala REST backend to allow retrieval of movie info and posters.

### Running locally

Environment variables required by the service can be stored in a `.env` file at
the project root. `start.sh` will load these values and pass them to
`docker run` when launching the container. An example is provided in
`.env.example`.

To start the service:

```
./start.sh
```

Variables can also be supplied through the environment, which is useful in a CI
pipeline.

### Docker

To run in production mode inside a Docker container we first have to build the image. E.g.

```
docker build -t nanothermite/ratingslave:rest .
```

The aforementioned command will build the image and tag it with the latest commit hash.

To run the image manually you can execute `start.sh`, which passes the
environment variables from `.env` and exposes ports `8081` and `5050`:

```
./start.sh
```

To attach to the running container via shell:

```
docker exec -it <imagehash> /bin/bash
```

REST syntax
GET http://localhost:8080/reldate/YYYY/MM/rating

where rating is a double ranging from 1 to 10.
