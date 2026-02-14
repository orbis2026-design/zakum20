@echo off
echo ========================================
echo  ZAKUM SUITE - BUILD ALL
echo ========================================
echo.

echo Building all modules...
call gradlew.bat clean build --no-daemon

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo  BUILD SUCCESSFUL
    echo ========================================
    echo.
    echo JARs created in:
    echo   zakum-core\build\libs\
    echo   zakum-battlepass\build\libs\
    echo   zakum-crates\build\libs\
    echo   ... etc
    echo.
) else (
    echo.
    echo ========================================
    echo  BUILD FAILED
    echo ========================================
    echo Check errors above
)

pause
