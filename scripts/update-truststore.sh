#!/usr/bin/env bash
set -euo pipefail

HOSTS=(
  "vps.iscs-i.com:465"
  "api.helcim.com:443"
)

KEYSTORE=${KEYSTORE:-client-truststore.jks}
PASSWORD=${PASSWORD:-changeit}
DEFAULT_STOREPASS=${DEFAULT_STOREPASS:-changeit}

# Resolve the default JDK cacerts file from the current java binary
JAVA_BIN=$(command -v java)
if [[ -z "$JAVA_BIN" ]]; then
  echo "java binary not found on PATH" >&2
  exit 1
fi
DEFAULT_CACERTS="$(dirname "$(dirname "$JAVA_BIN")")/lib/security/cacerts"

if [[ ! -f "$DEFAULT_CACERTS" ]]; then
  echo "Default cacerts not found at $DEFAULT_CACERTS" >&2
  exit 1
fi

# Seed the custom truststore with the standard JVM certificates
rm -f "$KEYSTORE"
keytool -importkeystore \
  -srckeystore "$DEFAULT_CACERTS" \
  -srcstorepass "$DEFAULT_STOREPASS" \
  -destkeystore "$KEYSTORE" \
  -deststorepass "$PASSWORD" \
  -noprompt > /dev/null

temp_cert=$(mktemp)

fetch_and_import() {
  local host_port=$1
  local host=${host_port%:*}
  local port=${host_port##*:}

  echo "Fetching certificate for $host:$port..."
  if echo | openssl s_client -connect "$host:$port" -servername "$host" 2>/dev/null | openssl x509 > "$temp_cert"; then
    keytool -delete -alias "$host" -keystore "$KEYSTORE" -storepass "$PASSWORD" -noprompt >/dev/null 2>&1 || true
    keytool -import -alias "$host" -file "$temp_cert" -keystore "$KEYSTORE" -storepass "$PASSWORD" -noprompt >/dev/null
    echo "Imported certificate for $host"
  else
    echo "Failed to fetch certificate from $host:$port" >&2
  fi
}

for host in "${HOSTS[@]}"; do
  fetch_and_import "$host"
done

rm -f "$temp_cert"

echo "Truststore updated: $KEYSTORE"
echo "Run the service with -Djavax.net.ssl.trustStore=$(pwd)/$KEYSTORE -Djavax.net.ssl.trustStorePassword=$PASSWORD"
