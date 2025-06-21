#!/usr/bin/env bash
set -euo pipefail

# Load variables from .env if present
if [ -f .env ]; then
  set -a
  . ./.env
  set +a
fi

docker run --net rs-net \
  --env PORT --env BINDHOST --env SERVERPOOL --env DATASOURCE \
  --env IMAGESOURCE \
  --env MONGOURI --env MONGORO --env DBNAME --env ORIGINS \
  --add-host=verbiet:192.168.4.50 \
  --restart on-failure \
  --name rs-svc-beta \
  -d -p 8081:8080 -p 5050:5050 nanothermite/ratingslave:latest
