#!/bin/bash
set -e

C_DIR="$PWD"
G_DIR=$(dirname $(readlink "$BASH_SOURCE" || echo "$BASH_SOURCE"))

# Parse gradle properties
VERS_JAVA=$( grep "vers_java=" "$G_DIR/gradle.properties" | cut -d = -f 2 )
VERS_ARTIFACT=$( grep "artifact_vers=" "$G_DIR/gradle.properties" | cut -d = -f 2 )

# Detect which version of Java we need
echo
echo "---- Build desired toolchain is Java $VERS_JAVA"
JV="java"

if ! grep -qs "$VERS_JAVA" $("$JV" -version 2>&1) ; then
  if [[ $(uname) =~ CYGWIN_.* || $(uname) =~ MSYS_.* ]]; then
    PF=$(cygpath "$PROGRAMFILES")
    JS=$(find "$PF/Java" -type f -name javac.exe | grep "$VERS_JAVA" | sort -r | head -n 1)
    JS=$(echo "$JS" | tr -d '\r')   # remove trailing \r if any
    JV="${JS/javac/java}"
    JS=$(cygpath -w "${JS//\/bin*/}")
  else
    JS=$(ls /usr/lib/jvm/*java*$VERS_JAVA*/bin/javac | head -n 1)
    JV="${JS/javac/java}"
    JS="${JS//\/bin*/}"
  fi
  if [[ -d "$JS" ]]; then
    export JAVA_HOME="$JS"
  else
    echo "---- Consider installing Java $VERS_JAVA and setting JAVA_HOME for it."
  fi
fi
echo "---- JAVA_HOME = $JAVA_HOME"
echo "---- JAVA      = $JV"

CONFIG=""
if [[ -f "$C_DIR/config.ini" ]]; then CONFIG="--config config.ini"; fi

# Build & invoke with command line args
U=$(uname)
if [[ "$U" =~ CYGWIN || "$U" =~ MSYS ]]; then
  # Windows.
  # Use the fatJar version to work around the issue of the classpath command line size limit.
  rm -f /tmp/_g
  set -x
  (cd $G_DIR && ./gradlew assemble _printRunFatJarCmdLine --console=plain | tee /tmp/_g)
  JAR=$(grep -- "-jar" /tmp/_g | head -n 1 | cut -c 6- | tr -d '\r')  # remove trailing \r if any
  "$JV" -jar "$JAR" $CONFIG $@
else
  # Linux
  # Option 1: cd $G_DIR && ./gradlew run -Pargs="$CONFIG $@" --console=plain
  # ==> this works but we loose terminal output formatting from train-motion + keyboard input.
  # Option 2: ./gradlew _printRunCmdLine and execute directly from here.
  rm -f /tmp/_g
  set -x
  (cd $G_DIR && ./gradlew _printRunCmdLine --console=plain | tee /tmp/_g)
  "$JV" $(grep -- "-cp" /tmp/_g | head -n 1) $CONFIG $@
fi

