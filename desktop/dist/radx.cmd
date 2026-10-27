@echo off
rem Radx launcher: checks the Java runtime first, because the bundled start script
rem would otherwise fail with a cryptic "class not found" on an old JVM.
setlocal EnableDelayedExpansion

set "RADX_JAVA=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "RADX_JAVA=%JAVA_HOME%\bin\java.exe"

"%RADX_JAVA%" -version > "%TEMP%\radx-version.txt" 2>&1
if errorlevel 1 goto nojava

set "VER="
for /f "usebackq tokens=3" %%V in ("%TEMP%\radx-version.txt") do if not defined VER set "VER=%%V"
if not defined VER goto nojava
set "VER=!VER:"=!"
for /f "tokens=1 delims=._-" %%M in ("!VER!") do set "MAJOR=%%M"

if "!MAJOR!"=="1" goto oldjava
if !MAJOR! LSS 17 goto oldjava
goto run

:nojava
echo.
echo Radx needs Java 17 or newer and could not find a java executable.
echo   * install Temurin 17+ from https://adoptium.net/ , or
echo   * point JAVA_HOME at an existing JDK, for example:
echo       set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17
echo.
exit /b 1

:oldjava
echo.
echo Radx needs Java 17 or newer, but "!RADX_JAVA!" reports !VER!.
echo Set JAVA_HOME to a JDK 17+ or install Temurin from https://adoptium.net/ .
echo.
exit /b 1

:run
rem %~dp0 ends with a backslash, so the folder next to this script is %~dp0lib .
"%RADX_JAVA%" -cp "%~dp0lib\*" com.radx.desktop.MainKt %*
