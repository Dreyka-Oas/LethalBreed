@echo off
REM ==========================================================================
REM  Build the DEV jar — for local testing only, NOT for distribution. Bundles
REM  the main source set PLUS the dev source set: headless test harnesses and
REM  the /lethaldev + /lethalspawn commands. DevBootstrap still gates its wiring
REM  behind isDevelopmentEnvironment(), so even this jar only activates the dev
REM  hooks in a dev environment.
REM
REM  Output: build\libs\lethalbreed-<version>-dev.jar
REM ==========================================================================
setlocal
REM Scripts live in scripts\ — run gradle from the repo root (parent dir).
cd /d "%~dp0.."

echo [build-dev] Building DEV jar (includes test harnesses + dev commands)...
call gradlew.bat clean remapDevJar %*
if errorlevel 1 (
    echo [build-dev] BUILD FAILED
    exit /b 1
)

echo.
echo [build-dev] Done. Dev jar in build\libs\:
dir /b build\libs\*-dev.jar 2>nul
endlocal
