# Rating Slave

Working Scala REST backend to allow retrieval of movie info and posters.

Even requests to the free endpoints should include an `Authorization: Bearer <token>`
header so activity can be attributed to a user. Calls without a valid token are
still served but the requester will be logged as `guest`.

Search history entries are stored with a compound unique index on `userId` and
`sig` so identical queries from different users don't clash.

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

### Autosuggest and search examples

Postman-ready cURL samples for autosuggest (`/api/v3/autoname`, `/api/v3/autotitle`) are in
[`docs/autosuggest-endpoints.md`](docs/autosuggest-endpoints.md). Full-response name/title search
examples live in [`docs/search-endpoints.md`](docs/search-endpoints.md).

## Billing integrations

Paid signups trigger an automated Helcim provisioning flow powered by
[`helcim4s`](https://github.com/iscs/helcim4s). When the `plan` is set to a pro
tier the backend will create a Helcim customer, attach the configured payment
plan and persist the resulting subscription snapshot. See
[`docs/helcim-integration.md`](docs/helcim-integration.md) for the required
environment variables and an example signup payload containing the billing
details (name, address and HelcimPay token) that the API expects.
