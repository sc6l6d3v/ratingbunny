#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "Usage: $0 DEV|PROD [PORT]" >&2
  exit 1
}

# --- args / env --------------------------------------------------------------
ENV_IN="${1:-}"; [[ -z "${ENV_IN}" ]] && usage
ENV_UPPER="$(echo "${ENV_IN}" | tr '[:lower:]' '[:upper:]')"
case "${ENV_UPPER}" in DEV|PROD) ;; *) echo "ENV must be DEV or PROD"; exit 2;; esac

PORT="${2:-${PORT:-8080}}"

# load .env if present (ignores comments/blank lines)
if [[ -f .env ]]; then
  export $(grep -vE '^\s*(#|$)' .env | xargs) || true
fi

: "${HUBUSER:?HUBUSER must be set (e.g. export HUBUSER=yourdockerhubuser)}"

IMAGE="${HUBUSER}/ratingslave"
TS="$(date +%Y%m%d%H%M)"
STAMP_TAG="${TS}"
REST_TAG="rest-${ENV_UPPER}"

echo "==> Building ${IMAGE}:${STAMP_TAG} and ${IMAGE}:latest (PORT=${PORT})"
PORT="${PORT}" docker build \
  -t "${IMAGE}:${STAMP_TAG}" \
  -t "${IMAGE}:latest" \
  .

echo "==> Tagging ${IMAGE}:${REST_TAG} from ${IMAGE}:${STAMP_TAG}"
docker tag "${IMAGE}:${STAMP_TAG}" "${IMAGE}:${REST_TAG}"

echo "==> Pushing ${IMAGE}:${STAMP_TAG}"
docker push "${IMAGE}:${STAMP_TAG}"

echo "==> Pushing ${IMAGE}:${REST_TAG}"
docker push "${IMAGE}:${REST_TAG}"

# Optional: push :latest if you want it updated too
if [[ "${PUSH_LATEST:-0}" == "1" ]]; then
  echo "==> Pushing ${IMAGE}:latest"
  docker push "${IMAGE}:latest"
fi

echo "Done."
echo "  Built   : ${IMAGE}:${STAMP_TAG}"
echo "  Tagged  : ${IMAGE}:${REST_TAG}"
echo "  Pushed  : ${IMAGE}:${STAMP_TAG}, ${IMAGE}:${REST_TAG}"
[[ "${PUSH_LATEST:-0}" == "1" ]] && echo "  Pushed  : ${IMAGE}:latest"
