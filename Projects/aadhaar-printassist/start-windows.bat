@echo off
setlocal
cd /d "%~dp0"
if not exist .venv\Scripts\python.exe (
  echo First run: preparing a local Python environment...
  py -3 -m venv .venv
  if errorlevel 1 goto failed
  .venv\Scripts\python.exe -m pip install -r requirements.txt
  if errorlevel 1 goto failed
)
.venv\Scripts\python.exe run.py
if errorlevel 1 goto failed
exit /b 0
:failed
echo Setup or startup failed. See README.md. Python 3.11+ with Tcl/Tk is required for source installation.
pause
exit /b 1
