@echo off
setlocal

set "MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.9"
set "MVN_EXE=%MAVEN_HOME%\bin\mvn.cmd"

if not exist "%MVN_EXE%" (
    echo Downloading Apache Maven 3.9.9...
    powershell -NoProfile -Command ^
      "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; $zip='%TEMP%\maven.zip'; Invoke-WebRequest 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip' -OutFile $zip; Expand-Archive $zip '%USERPROFILE%\.m2\wrapper\dists' -Force; Remove-Item $zip"
    if errorlevel 1 (
        echo Failed to download Maven. Please install Maven manually from https://maven.apache.org/download.cgi
        exit /b 1
    )
)

"%MVN_EXE%" %*
endlocal
