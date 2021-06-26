#!/bin/bash
set -e
set -x
./gradlew ass fatJar --info
# ./gradlew --stop
ls -1sh $(find build/ -name "*.jar")
java -jar build/libs/train-motion-0.1-SNAPSHOT-all.jar $@
