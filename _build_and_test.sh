#!/bin/bash
set -e

# Figure the directory where this script is located (following symlinks as needed)
PROG_DIR=$(dirname $(readlink "$BASH_SOURCE" || echo "$BASH_SOURCE"))

# Parse gradle properties
VERS_JAVA=$( grep "vers_java=" "$PROG_DIR/gradle.properties" | cut -d = -f 2 )
VERS_ARTIFACT=$( grep "artifact_vers=" "$PROG_DIR/gradle.properties" | cut -d = -f 2 )

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
    JS=$(ls /usr/lib/jvm/*java*$JV*/bin/javac | head -n 1)
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


# Change to this script directory to build (following symlinks as needed)
(
    cd "$PROG_DIR"
    pwd

    # Build
    set -x
    ./gradlew test ass fatJar --console=plain --info
)

# Note: we don't CD to the script dir to respect a local config.ini in the exec dir context.
DST="$PROG_DIR/build"
JAR="$DST/libs/train-motion-${VERS_ARTIFACT}-all.jar"

# List & run
ls -1sh $(find $DST/ -name "*.jar")
set -x
"$JV" -jar "$JAR" --help

