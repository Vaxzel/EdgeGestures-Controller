@echo off
setlocal enabledelayedexpansion

echo ============================================================
echo   Edge Gestures Controller - Build Script
echo ============================================================

if exist "gradlew.bat" (
    echo Building using Gradle Wrapper...
    call gradlew.bat assembleRelease
    goto end
)

where gradle >nul 2>nul
if %errorlevel% equ 0 (
    echo Building using installed Gradle...
    call gradle assembleRelease
    goto end
)

echo [!] Gradle not found in PATH.
echo Please install Gradle or open this project in Android Studio.

:end
echo ============================================================
