$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if ($env:OS -ne 'Windows_NT') { throw 'Build the Windows executable on Windows.' }
py -3 -m venv .venv
if ($LASTEXITCODE -ne 0) { throw 'Install Python 3.11+ with Tcl/Tk.' }
& .\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt
if ($LASTEXITCODE -ne 0) { throw 'Dependency installation failed.' }
& .\.venv\Scripts\python.exe -m pytest -q
if ($LASTEXITCODE -ne 0) { throw 'Tests failed.' }
& .\.venv\Scripts\python.exe -m PyInstaller --noconfirm --clean --windowed --onedir --name AadhaarPrintAssist --icon assets\printassist.ico --add-data "assets;assets" --collect-all pypdfium2 --collect-all pypdfium2_raw --collect-all pyhanko --collect-all pyhanko_certvalidator --hidden-import PIL.ImageTk --hidden-import PIL.ImageWin run.py
if ($LASTEXITCODE -ne 0) { throw 'Build failed.' }
Copy-Item -Recurse -Force trust dist\AadhaarPrintAssist\trust
Copy-Item -Recurse -Force assets dist\AadhaarPrintAssist\assets
Copy-Item setup-desktop.ps1 dist\AadhaarPrintAssist\setup-desktop.ps1
Copy-Item Create-Desktop-Shortcut.bat dist\AadhaarPrintAssist\Create-Desktop-Shortcut.bat
Copy-Item README.md dist\AadhaarPrintAssist\README.md
Copy-Item LICENSE dist\AadhaarPrintAssist\LICENSE
Copy-Item -Recurse -Force docs dist\AadhaarPrintAssist\docs
Write-Host 'Built dist\AadhaarPrintAssist\AadhaarPrintAssist.exe. Distribute the entire folder.'
Write-Host 'Use the Windows acceptance checklist before a shop rollout.'
