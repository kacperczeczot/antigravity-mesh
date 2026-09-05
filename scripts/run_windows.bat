@echo off
title Antigravity Mesh Node Daemon
echo ===================================================
echo   Antigravity Mesh Node Daemon (Windows)
echo ===================================================
python "%~dp0..\daemon\server.py" %*
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Daemon stopped with error code %ERRORLEVEL%
    pause
)
