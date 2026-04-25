$env:JAVA_HOME = "C:\Users\perso\scoop\apps\graalvm-oracle-21jdk\21.0.8"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
Set-Location "W:\Minecraft\Projets\LethalBreed\fabric\1.21.11"
Write-Host "=== FABRIC 1.21.11 CLIENT ===" -ForegroundColor Cyan
Write-Host "Java: $env:JAVA_HOME" -ForegroundColor Yellow
Write-Host "Lancement du client..." -ForegroundColor Green
.\gradlew.bat runClient