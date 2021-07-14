#!/bin/bash
set -e
set -x
(
    # Change to this script direcoty (following symlinks as needed)
    cd $(dirname $(readlink "$BASH_SOURCE" || echo "$BASH_SOURCE"))
    # Build
    ./gradlew ass fatJar --info
    # ./gradlew --stop
)
# List & run
ls -1sh $(find build/ -name "*.jar")
java -jar build/libs/train-motion-0.1-SNAPSHOT-all.jar $@
