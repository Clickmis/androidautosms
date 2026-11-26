@echo off
where gradle >nul 2>nul
if errorlevel 1 (
  echo Gradle is not installed. Open this project in Android Studio or install Gradle 8.11.1+.
  exit /b 1
)
gradle assembleDebug
if errorlevel 1 exit /b 1
echo APK: app\build\outputs\apk\debug\app-debug.apk
