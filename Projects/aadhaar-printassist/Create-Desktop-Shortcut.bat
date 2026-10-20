@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup-desktop.ps1"
if errorlevel 1 (
  echo Shortcut setup did not complete. Please review the error above.
)
pause
