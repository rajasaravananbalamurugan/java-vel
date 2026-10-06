@echo off
setlocal
cd /d "%~dp0"

if not exist target\classes mkdir target\classes

echo [1/2] Compiling Java sources...
javac -encoding UTF-8 -d target\classes ^
src\main\java\com\atm\infrastructure\*.java ^
src\main\java\com\atm\model\*.java ^
src\main\java\com\atm\exception\*.java ^
src\main\java\com\atm\dao\*.java ^
src\main\java\com\atm\service\*.java ^
src\main\java\com\atm\synchronization\*.java ^
src\main\java\com\atm\ui\*.java ^
src\main\java\com\atm\main\*.java

if errorlevel 1 (
    echo.
    echo Compilation failed.
    pause
    exit /b 1
)

echo.
echo [2/2] MySQL Connector/J must be supplied at runtime.
echo Example:
echo java -cp "target\classes;lib\mysql-connector-j-8.3.0.jar" com.atm.main.Offatm
echo.
pause
endlocal
