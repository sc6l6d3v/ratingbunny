#!/usr/bin/env bash
# Indexes refresh tokens that were issued before TokenIssuer kept a per-user index
# (refresh-user:<uid>), so revokeUser - run on password reset - also ends those sessions.
# Run once against each Redis right after deploying that change. Idempotent.
#
#   REDISCLI="redis-cli -h <host> -a <password>" scripts/backfill-refresh-index.sh
set -euo pipefail

CLI=${REDISCLI:-redis-cli}

LUA='local uid = redis.call("GET", KEYS[1])
if not uid then return 0 end
local ttl = redis.call("TTL", KEYS[1])
if ttl <= 0 then return 0 end
local idx = "refresh-user:" .. uid
redis.call("ZADD", idx, tonumber(ARGV[1]) + ttl, KEYS[1])
if redis.call("TTL", idx) < ttl then redis.call("EXPIRE", idx, ttl) end
return 1'

now=$(date +%s)
indexed=0
while read -r key; do
  [[ -z "$key" ]] && continue
  added=$($CLI --no-auth-warning EVAL "$LUA" 1 "$key" "$now")
  indexed=$((indexed + added))
done < <($CLI --no-auth-warning --scan --pattern 'refresh:*')

echo "indexed $indexed refresh tokens"
