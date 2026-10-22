@echo off
title IntelliDesk
color 0A

:: ── Find Java ──────────────────────────────────────────────────────────────
set "JAVA_EXE="
if exist "%USERPROFILE%\AppData\Local\Programs\Eclipse Adoptium\jdk-17.0.8.101-hotspot\bin\java.exe" (
    set "JAVA_EXE=%USERPROFILE%\AppData\Local\Programs\Eclipse Adoptium\jdk-17.0.8.101-hotspot\bin\java.exe"
) else (
    set "JAVA_EXE=java"
)

:: ── Build JAR if missing ───────────────────────────────────────────────────
set "JAR=%~dp0target\intellidesk-0.1.0.jar"
if not exist "%JAR%" (
    echo [*] JAR not found. Building project...
    call "%~dp0mvnw.cmd" package -DskipTests
    if errorlevel 1 (
        echo.
        echo [ERROR] Build failed. See output above.
        pause
        exit /b 1
    )
)

:: ── Free port 8080 if something is already using it ───────────────────────
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":8080 " ^| findstr LISTENING') do (
    echo [*] Port 8080 in use by PID %%p — stopping it...
    taskkill /PID %%p /F >nul 2>&1
    timeout /t 1 /nobreak >nul
)

:: ── Launch ─────────────────────────────────────────────────────────────────
echo.
echo  ==========================================
echo    IntelliDesk is starting...
echo    Open your browser to:
echo      http://localhost:8080
echo.
echo    Demo logins (password: password)
echo      Admin    : admin
echo      Tech     : marcus.webb
echo      Employee : james.carter
echo.
echo    Press Ctrl+C to stop the server.
echo  ==========================================
echo.

:: Open browser after a short delay (runs in background)
start "" cmd /c "timeout /t 5 /nobreak >nul && start http://localhost:8080"

"%JAVA_EXE%" -jar "%JAR%" --spring.profiles.active=h2

echo.
echo  Server stopped.
pause
