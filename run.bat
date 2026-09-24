@echo off
title CAMS - Embedded Tomcat Server
echo =================================================
echo  Starting CAMS Embedded Apache Tomcat Server...
echo =================================================
set "JAVA_HOME=C:\Users\FAIZAL\AppData\Local\Programs\Eclipse Adoptium\jdk-17.0.16.8-hotspot"
call mvnw.cmd compile exec:java -Dexec.mainClass="com.cams.Server"
pause
