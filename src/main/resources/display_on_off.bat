@ECHO OFF
REM Invoked by train-motion when the display is being turned on or off.
REM
REM Receives one argument:
REM - start : Display Controller just started.
REM - on    : Display Controller wants the screen turned on.
REM - off   : Display Controller wants the screen turned off.
REM - stop  : Display Controller is terminating. Screen should probably be turned on if needed.

ECHO Argument: %1

IF "%1" == "start" ( ECHO Display: Start )
IF "%1" == "on"    ( ECHO Display: On    )
IF "%1" == "off"   ( ECHO Display: Off   )
IF "%1" == "stop"  ( ECHO Display: Stop  )
