#!/bin/bash
# vim: set ts=2 sw=2:

cd $(dirname "$0")
DIR=$PWD
URL="http://localhost:8080/"

CHROME="google-chrome"
if [[ ! -f "$CHROME" ]]; then
	CHROME="chromium"
fi

# Note: don't make kiosk the default yet. Good for prod, not for experimenting.
# Full-screen can be toggled off using F11. Kiosk mode disables most interaction
# which is not expected to be needed here.
FS="--start-fullscreen"
if [[ "$1" == "-f" ]]; then
  FS="$FS --kiosk"
fi

while true; do
  echo
  echo -e "Starting Kiosk for $URL\n"
  sleep 1s
  $CHROME $FS --incognito --password-store=basic "$URL"
  sleep 5s
done
