@echo off
cd /d "%~dp0\.."

if exist "run\.fabric\processedMods" rmdir /s /q "run\.fabric\processedMods"
call .\gradlew.bat runClient --console=plain
pause
