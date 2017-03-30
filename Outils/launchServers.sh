#!/bin/bash

wPort=8000
hPort=8001
mPort=8002
size=100

java -cp . WelcomeServer $wPort $size &
java -cp . HashServer $hPort $size &
java -cp . MonitorServer localhost $wPort $mPort
