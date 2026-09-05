@echo off
title Antigravity Mesh Node Daemon
echo ===================================================
echo   Antigravity Mesh Node Daemon (Windows)
echo ===================================================
if exist "%~dp0..\daemon-rs\target\release\daemon-rs.exe" (
    echo Starting native Rust daemon...
    "%~dp0..\daemon-rs\target\release\daemon-rs.exe" %*
) else if exist "%~dp0..\AntigravityMesh-Windows.exe" (
    echo Starting native executable...
    "%~dp0..\AntigravityMesh-Windows.exe" %*
) else (
    echo Starting Python daemon fallback...
    python "%~dp0..\daemon\server.py" %*
)
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Daemon stopped with error code %ERRORLEVEL%
    pause
)

