@echo off
chcp 65001 >nul
title IoT Demo - CMD Client
cd /d "%~dp0java-client"
set "MQTT_BROKER=tcp://127.0.0.1:1883"
set "MQTT_TOPIC_PREFIX=lopmang-iot/esp32-demo-01"
call mvn compile exec:java -Pcmd
pause
