@echo off
setlocal

where mvn >nul 2>nul
if errorlevel 1 (
  echo Maven was not found. Please install Maven or add mvn to PATH.
  exit /b 1
)

echo Installing local lavabili plugin...
call mvn install:install-file ^
  -Dfile=libs\lavabili-plugin-1.4.1.jar ^
  -DgroupId=com.github.ParrotXray ^
  -DartifactId=lavabili-plugin ^
  -Dversion=1.4.1 ^
  -Dpackaging=jar ^
  -DgeneratePom=true
if errorlevel 1 exit /b 1

echo Building SonwMusicBot...
call mvn -Dmaven.test.skip=true package
if errorlevel 1 exit /b 1

if exist target\JMusicBot-0.6.5-All.jar (
  move /Y target\JMusicBot-0.6.5-All.jar target\SonwMusicBot-0.6.5.jar >nul
)

echo.
echo Done.
echo Fat jar:
echo target\SonwMusicBot-0.6.5.jar
