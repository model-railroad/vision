#!/bin/bash
set -e

C_DIR="$PWD"
G_DIR=$(dirname $(readlink "$BASH_SOURCE" || echo "$BASH_SOURCE"))

echo "JAVA_HOME=${JAVA_HOME}"
[[ -d "$JAVA_HOME" ]] && JV="$JAVA_HOME/bin/java.exe" || JV=$(which java)
V=$( "$JV" -version 2>&1 | sed -n -e '/build /s/.*build \([0-9]\+\.[0-9]\+\).*/\1/p' | head -n 1 )
echo "Java version: $V"

CONFIG=""
if [[ -f "$C_DIR/config.ini" ]]; then CONFIG="--config config.ini"; fi

# Build & invoke with command line args
if [[ $(uname) =~ CYGWIN ]]; then
  # Windows.
  # Use the fatJar version to work around the issue of the classpath command line size limit.
  rm -f /tmp/_g
  set -x
  (cd $G_DIR && ./gradlew _printRunFatJarCmdLine --console=plain | tee /tmp/_g)
  JAR=$(grep -- "-jar" /tmp/_g | head -n 1 | cut -c 6- )
  "$JV" -jar "$JAR" $CONFIG $@
else
  # Linux
  # Option 1: cd $G_DIR && ./gradlew run -Pargs="$CONFIG $@" --console=plain
  # ==> this works but we loose terminal output formatting from train-motion + keyboard input.
  # Option 2: ./gradlew _printRunCmdLine and execute directly from here.
  rm -f /tmp/_g
  set -x
  (cd $G_DIR && ./gradlew _printRunCmdLine --console=plain | tee /tmp/_g)
  $JV $(grep -- "-cp" /tmp/_g | head -n 1) $CONFIG $@
fi

