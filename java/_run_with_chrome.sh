#!/bin/bash
set -e
set -x
chromium --start-fullscreen  --incognito --password-store=basic http://localhost:8080/ &
java -jar build/libs/cam-proxy-0.1-SNAPSHOT-all.jar $@ 
killall --signal TERM /usr/lib/chromium/chromium

