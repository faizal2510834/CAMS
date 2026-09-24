# CAMS - Run Embedded Tomcat Server
Write-Host "=================================================" -ForegroundColor Cyan
Write-Host " Starting CAMS Embedded Apache Tomcat Server... " -ForegroundColor Cyan
Write-Host "=================================================" -ForegroundColor Cyan

$env:JAVA_HOME = "C:\Users\FAIZAL\AppData\Local\Programs\Eclipse Adoptium\jdk-17.0.16.8-hotspot"
.\mvnw.cmd compile exec:java -Dexec.mainClass="com.cams.Server"
