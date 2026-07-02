@echo off
cd /d "%~dp0\.."

:: Nettoyer les caches Fabric pour eviter les conflits
echo Nettoyage des caches...
if exist "run\client1\.fabric\processedMods" rmdir /s /q "run\client1\.fabric\processedMods"
if exist "run\client2\.fabric\processedMods" rmdir /s /q "run\client2\.fabric\processedMods"
if exist "run\server\.fabric\processedMods" rmdir /s /q "run\server\.fabric\processedMods"

echo Demarrage du serveur...
start "Serveur" cmd /k .\gradlew.bat runServer --console=plain

:: Attendre quelques secondes pour que le serveur soit pret
timeout /t 10 /nobreak >nul

echo Demarrage du client 1...
start "Client 1" cmd /k .\gradlew.bat runClient1 --console=plain

timeout /t 5 /nobreak >nul

echo Demarrage du client 2...
start "Client 2" cmd /k .\gradlew.bat runClient2 --console=plain

echo Tous les composants sont lancer dans des fenetres separees.
echo Appuyez sur une touche pour fermer cette fenetre.
pause