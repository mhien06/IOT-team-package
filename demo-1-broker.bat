@echo off
chcp 65001 >nul
title IoT Demo - Java Broker
cd /d "%~dp0java-broker"
set "MQTT_HOST=127.0.0.1"
set "MQTT_PORT=1883"
call mvn compile exec:java
pause
