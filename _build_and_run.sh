#!/bin/bash
set -e

C_DIR="$PWD"
G_DIR=$(dirname $(readlink "$BASH_SOURCE" || echo "$BASH_SOURCE"))

echo "JAVA_HOME=${JAVA_HOME}"
[[ -d "$JAVA_HOME" ]] && JV="$JAVA_HOME/bin/java.exe" || JV=$(which java)
V=$( "$JV" -version 2>&1 | sed -n -e '/build /s/.*build \([0-9]\+\.[0-9]\+\).*/\1/p' | head -n 1 )
echo "Java version: $V"

CONFIG=""
if [[ -f "$C_DIR/config.ini" ]]; then CONFIG="--config $C_DIR/config.ini"; fi

# Build & invoke with command line pargs
set -x
cd $G_DIR && ./gradlew run -Pargs="$CONFIG $@" --console=plain

