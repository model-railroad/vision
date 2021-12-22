#!/bin/bash
set -e

# Figure the directory where this script is located (following symlinks as needed)
PROG_DIR=$(dirname $(readlink "$BASH_SOURCE" || echo "$BASH_SOURCE"))

(
    # Change to this script directory (following symlinks as needed)
    cd "$PROG_DIR"

    echo "JAVA_HOME=${JAVA_HOME}"
    [[ -d "$JAVA_HOME" ]] && JV="$JAVA_HOME/bin/java.exe" || JV=$(which java)
    V=$( "$JV" -version 2>&1 | sed -n -e '/build /s/.*build \([0-9]\+\.[0-9]\+\).*/\1/p' | head -n 1 )
    echo "Java version: $V"

    # Build
    set -x
    ./gradlew test ass fatJar --console=plain --info
    # ./gradlew --stop
)

# Note: we don't CD to the script dir to respect a local config.ini in the exec dir context.
DST="$PROG_DIR/build"
JAR="$DST/libs/train-motion-0.5-SNAPSHOT-all.jar"

# List & run
ls -1sh $(find $DST/ -name "*.jar")
set -x
java -jar $JAR --help

