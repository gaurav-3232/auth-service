#!/bin/sh
set -e
DIST_URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip"
MVN_DIR="$HOME/.m2/wrapper/dists/apache-maven-3.9.9"
if [ ! -d "$MVN_DIR" ]; then
  echo "Downloading Maven..." && mkdir -p "$MVN_DIR"
  TMP=$(mktemp /tmp/mvn.XXXXXX.zip)
  curl -fsSL -o "$TMP" "$DIST_URL" && unzip -q -o "$TMP" -d "$MVN_DIR" && rm -f "$TMP"
fi
MVN_BIN=$(find "$MVN_DIR" -name "mvn" -path "*/bin/mvn" | head -1)
exec "$MVN_BIN" "$@"
