$env:JAVA_HOME = "C:\Users\perso\scoop\apps\graalvm-oracle-21jdk\21.0.8"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
Set-Location "W:\Minecraft\Projets\LethalBreed\neoforge"
Write-Host "=== NEOFORGE 1.20.2 CLIENT ===" -ForegroundColor Cyan
.\gradlew.bat runClient