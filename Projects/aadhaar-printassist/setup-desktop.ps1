# Creates per-user shortcuts; no administrator rights or registry changes.
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if ($env:OS -ne 'Windows_NT') { throw 'Run this launcher setup on Windows.' }
try {
    $exe = Join-Path $PSScriptRoot 'AadhaarPrintAssist.exe'
    $arguments = ''
    if (-not (Test-Path -LiteralPath $exe)) {
        $python = Join-Path $PSScriptRoot '.venv\Scripts\python.exe'
        if (-not (Test-Path -LiteralPath $python)) {
            & py -3 -m venv (Join-Path $PSScriptRoot '.venv')
            if ($LASTEXITCODE -ne 0) { throw 'Install Python 3.11 or newer with Tcl/Tk and the Python launcher.' }
        }
        & $python -m pip install -r (Join-Path $PSScriptRoot 'requirements.txt')
        if ($LASTEXITCODE -ne 0) { throw 'Dependency setup failed. Check the internet connection and retry.' }
        $exe = Join-Path $PSScriptRoot '.venv\Scripts\pythonw.exe'
        $arguments = '"' + (Join-Path $PSScriptRoot 'run.py') + '"'
    }
    $shell = New-Object -ComObject WScript.Shell
    $desktop = [Environment]::GetFolderPath('Desktop')
    $programs = [Environment]::GetFolderPath('Programs')
    foreach ($folder in @($desktop, $programs)) {
        $shortcut = $shell.CreateShortcut((Join-Path $folder 'Aadhaar PrintAssist.lnk'))
        $shortcut.TargetPath = $exe
        $shortcut.Arguments = $arguments
        $shortcut.WorkingDirectory = $PSScriptRoot
        $shortcut.IconLocation = (Join-Path $PSScriptRoot 'assets\printassist.ico') + ',0'
        $shortcut.Description = 'Open, verify and print customer-authorized Aadhaar PDFs locally'
        $shortcut.Save()
    }
    Write-Host 'Ready. Open Aadhaar PrintAssist from the Desktop or Start menu.'
    Write-Host 'Keep this entire extracted folder here. Run setup again if you move it.'
} catch {
    Write-Host ('Setup failed: ' + $_.Exception.Message) -ForegroundColor Red
    exit 1
}
