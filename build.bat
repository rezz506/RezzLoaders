@echo off
setlocal

REM Builds RezzLoaders without requiring a system Gradle install.
REM Requires: Java 21 installed (JAVA_HOME optional), internet access for dependency downloads.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0bootstrap-gradle.ps1"
if errorlevel 1 exit /b 1

call "%~dp0gradlew.bat" build
if errorlevel 1 exit /b 1

echo.
echo Build complete! Your jar is in: build\libs\RezzLoaders-1.0.0.jar
endlocal
