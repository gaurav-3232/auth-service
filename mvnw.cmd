@echo off
set "DIST_URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip"
set "MVN_DIR=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.9"
if not exist "%MVN_DIR%" (
  echo Downloading Maven...
  mkdir "%MVN_DIR%" 2>nul
  powershell -Command "Invoke-WebRequest -Uri '%DIST_URL%' -OutFile '%TEMP%\maven.zip'"
  powershell -Command "Expand-Archive -Path '%TEMP%\maven.zip' -DestinationPath '%MVN_DIR%' -Force"
  del "%TEMP%\maven.zip" 2>nul
)
for /f "delims=" %%i in ('dir /s /b "%MVN_DIR%\mvn.cmd" 2^>nul') do set "MVN_BIN=%%i"
"%MVN_BIN%" %*
