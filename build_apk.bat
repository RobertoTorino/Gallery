@echo off
setlocal

:: Try to find Android Studio's JBR (standard path)
set "STUDIO_JDK=C:\Program Files\Android\Android Studio\jbr"
if not exist "%STUDIO_JDK%" set "STUDIO_JDK=C:\Program Files\Android\Android Studio\jre"

if exist "%STUDIO_JDK%" (
    echo Using Android Studio JDK: %STUDIO_JDK%
    set "JAVA_HOME=%STUDIO_JDK%"
    set "PATH=%JAVA_HOME%\bin;%PATH%"
) else (
    echo WARNING: Android Studio JDK not found in default path.
    echo If this fails, please set JAVA_HOME manually.
)

echo.
set /p CHOICE="Perform a clean build? (y/n, default is n): "

if /i "%CHOICE%"=="y" (
    echo Cleaning and building APK...
    call gradlew.bat clean assembleDebug
) else (
    echo Building APK...
    call gradlew.bat assembleDebug
)

if %ERRORLEVEL% EQU 0 (
    echo.
    echo Build Successful!
    echo APK location: app\build\outputs\apk\debug\gallery-debug.apk
    explorer "app\build\outputs\apk\debug"
) else (
    echo.
    echo Build Failed!
)

pause
endlocal