# Antigravity Mesh - Windows Startup Task Installer
# Registers a scheduled task to run the node daemon on user logon
$ErrorActionPreference = "Stop"

$ProjectDir = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$DaemonExe = Join-Path $ProjectDir "apps\daemon-rs\target\release\daemon-rs.exe"
if (-not (Test-Path $DaemonExe)) {
    $DaemonExe = Join-Path $ProjectDir "AntigravityMesh-Windows.exe"
}

if (Test-Path $DaemonExe) {
    Write-Host "🚀 Found native Rust daemon: $DaemonExe"
    $Action = New-ScheduledTaskAction -Execute $DaemonExe -Argument "--host 0.0.0.0 --port 8888" -WorkingDirectory $ProjectDir
} else {
    try {
        $PythonCmd = (Get-Command python.exe -ErrorAction Stop).Source
        $ServerScript = Join-Path $ProjectDir "apps\daemon-py\server.py"
        Write-Host "🐍 Using Python daemon fallback: $ServerScript"
        $Action = New-ScheduledTaskAction -Execute $PythonCmd -Argument "`"$ServerScript`"" -WorkingDirectory $ProjectDir
    } catch {
        Write-Error "Neither native executable nor Python was found. Please compile daemon-rs or install Python."
        exit 1
    }
}

$TaskName = "AntigravityMeshDaemon"
$Trigger = New-ScheduledTaskTrigger -AtLogOn
$Settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -ExecutionTimeLimit 0

Register-ScheduledTask -TaskName $TaskName -Action $Action -Trigger $Trigger -Settings $Settings -Description "Antigravity Mesh Node Daemon" -Force
Write-Host "✅ Task '$TaskName' registered successfully! It will start automatically upon logon."
Write-Host "To start it now, run: Start-ScheduledTask -TaskName '$TaskName'"
