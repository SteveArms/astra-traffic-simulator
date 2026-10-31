@echo off
rem Genera target\dist\ASTRA\ASTRA.exe (jlink + jpackage). Requiere JDK 21+ y Maven.
setlocal
cd /d "%~dp0"

where mvn >nul 2>nul
if errorlevel 1 (
    echo No se encontro Maven. Instalalo y agregalo al PATH.
    goto :fail
)

set "JPACKAGE=jpackage"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\jpackage.exe" set "JPACKAGE=%JAVA_HOME%\bin\jpackage.exe"
if "%JPACKAGE%"=="jpackage" (
    where jpackage >nul 2>nul
    if errorlevel 1 (
        echo No se encontro jpackage. Define JAVA_HOME con un JDK 21 o superior.
        goto :fail
    )
)

echo [1/2] Creando el runtime con jlink...
call mvn -q clean javafx:jlink
if errorlevel 1 (
    echo Si ASTRA esta abierto, cierralo: Maven no puede borrar target\dist.
    goto :fail
)

echo [2/2] Empaquetando con jpackage...
"%JPACKAGE%" --type app-image --name ASTRA --app-version 0.1.0 ^
    --runtime-image "%~dp0target\astra-runtime" ^
    --module com.astra/com.astra.app.AstraApp ^
    --dest "%~dp0target\dist"
if errorlevel 1 goto :fail

echo.
echo Listo: target\dist\ASTRA\ASTRA.exe
pause
exit /b 0

:fail
echo.
echo No se pudo generar el ejecutable. Revisa los mensajes de arriba.
pause
exit /b 1
