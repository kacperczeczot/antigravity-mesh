# Antigravity Mesh - Windows Startup Task Installer
# Registers a scheduled task to run the node daemon on user logon
$ErrorActionPreference = "Stop"

$ProjectDir = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$ServerScript = Join-Path $ProjectDir "apps\daemon-py\server.py"

try {
    $PythonCmd = (Get-Command python.exe -ErrorAction Stop).Source
} catch {
    Write-Error "Python was not found in PATH. Please install Python and add it to PATH."
    exit 1
}

$TaskName = "AntigravityMeshDaemon"
$Action = New-ScheduledTaskAction -Execute $PythonCmd -Argument "`"$ServerScript`"" -WorkingDirectory $ProjectDir
$Trigger = New-ScheduledTaskTrigger -AtLogOn
$Settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -ExecutionTimeLimit 0

Register-ScheduledTask -TaskName $TaskName -Action $Action -Trigger $Trigger -Settings $Settings -Description "Antigravity Mesh Node Daemon" -Force
Write-Host "✅ Task '$TaskName' registered successfully! It will start automatically upon logon."
Write-Host "To start it now, run: Start-ScheduledTask -TaskName '$TaskName'"
