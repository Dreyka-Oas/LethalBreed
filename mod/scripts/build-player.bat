@echo off
REM ==========================================================================
REM  Build the PLAYER jar — ships to users. Contains ONLY the main source set:
REM  no test harnesses, no /lethaldev or /lethalspawn dev commands, no dev
REM  bootstrap. This is the lean, distributable artifact.
REM
REM  Output: build\libs\lethalbreed-<version>.jar
REM ==========================================================================
setlocal
REM Scripts live in scripts\ — run gradle from the repo root (parent dir).
cd /d "%~dp0.."

echo [build-player] Building lean PLAYER jar (no dev code)...
call gradlew.bat clean build %*
if errorlevel 1 (
    echo [build-player] BUILD FAILED
    exit /b 1
)

echo.
echo [build-player] Done. Player jar in build\libs\:
dir /b build\libs\*.jar 2>nul
endlocal
