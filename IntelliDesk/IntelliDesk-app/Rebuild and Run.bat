@echo off
title IntelliDesk - Building...
color 0E

echo.
echo  Building IntelliDesk...
echo.

call "%~dp0mvnw.cmd" package -DskipTests
if errorlevel 1 (
    echo.
    echo [ERROR] Build failed. See output above.
    pause
    exit /b 1
)

echo.
echo  Build successful! Starting server...
echo.

call "%~dp0Run IntelliDesk.bat"
