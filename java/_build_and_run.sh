#!/bin/bash
set -e
set -x
./gradlew ass fatJar --info
ls -1sh $(find build/ -name "*.jar")
java -jar build/libs/cam-proxy-0.1-SNAPSHOT-all.jar --debug $@
