#!/bin/bash
#
# Invoked by train-motion when the display is being turned on or off.
#
# Receives one argument:
# - start : Display Controller just started.
# - on    : Display Controller wants the screen turned on.
# - off   : Display Controller wants the screen turned off.
# - stop  : Display Controller is terminating. Screen should probably be turned on if needed.

function screen_on() {
  echo "Turn screen on"
  /usr/bin/xset -display :0 dpms force on
  /usr/bin/xset s off -dpms   # screensaver off, no dpms
}

function screen_off() {
  echo "Turn screen off"
  /usr/bin/xset s on            # screensaver off, with dpms
  /usr/bin/xset dpms 10 20 30   # dmps standby/suspend/off timeouts (seconds)
}

case "$1" in
  "start" | "on" | "stop" )
    screen_on
    ;;
  "off" )
    screen_off
    ;;
esac

