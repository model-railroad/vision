#!/bin/bash
set -e
DST="build"
if [[ ! -d "build" ]]; then
    DST="train-motion/$DST"
fi
JAR="$DST/libs/train-motion-0.2-SNAPSHOT-all.jar"

if [[ ! -x "$JAR" || "$USER" != "vision" ]]; then
(
    # Change to this script direcoty (following symlinks as needed)
    cd $(dirname $(readlink "$BASH_SOURCE" || echo "$BASH_SOURCE"))

    echo "JAVA_HOME=${JAVA_HOME}"
    [[ -d "$JAVA_HOME" ]] && JV="$JAVA_HOME/bin/java.exe" || JV=$(which java)
    V=$( "$JV" -version 2>&1 | sed -n -e '/build /s/.*build \([0-9]\+\.[0-9]\+\).*/\1/p' | head -n 1 )
    echo "Java version: $V"

    # Build
    set -x
    ./gradlew test ass fatJar --console=plain --info
    # ./gradlew --stop
)
fi

# List & run
ls -1sh $(find $DST/ -name "*.jar")
set -x
java -jar $JAR --help
