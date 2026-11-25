# ==============================================================================
# Ritham ERP - Task Scheduler Setup Script
# ==============================================================================
# Purpose : Registers the daily backup task in Windows Task Scheduler.
# Run     : powershell -ExecutionPolicy Bypass -File "d:\ritham-erp\scripts\backup\setup-task-scheduler.ps1"
#
# To remove the task: Unregister-ScheduledTask -TaskName "Ritham ERP - Daily DB Backup" -Confirm:$false
# ==============================================================================

param(
    [ValidateRange(0, 23)]
    [int]$Hour = 19,
    [ValidateRange(0, 59)]
    [int]$Minute = 0,
    [switch]$Disable
)

$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Host "Notice: Running in standard user mode (task will run under current user)." -ForegroundColor Yellow
    Write-Host "For 24/7 background service execution without login, run as Administrator." -ForegroundColor Gray
}

# Configuration
$TASK_NAME        = "Ritham ERP - Daily DB Backup"
$TASK_DESCRIPTION = "Automatic daily backup of ritham_erp PostgreSQL database and uploaded files to selected backup folder."
$BACKUP_SCRIPT    = Join-Path $PSScriptRoot "erp-backup.ps1"
$BACKUP_HOUR      = $Hour     # Default 19 (7:00 PM)
$BACKUP_MINUTE    = $Minute
$TIME_24H         = "{0:D2}:{1:D2}" -f $BACKUP_HOUR, $BACKUP_MINUTE
$TIME_12H         = ([datetime]::Today.AddHours($BACKUP_HOUR).AddMinutes($BACKUP_MINUTE)).ToString("h:mm tt", [System.Globalization.CultureInfo]::InvariantCulture)
$RUN_AS_USER      = "$env:USERDOMAIN\$env:USERNAME"   # Current logged-in user

Write-Host ""
Write-Host ("=" * 60)
Write-Host "  Ritham ERP - Task Scheduler Setup"
Write-Host ("=" * 60)
Write-Host ""
Write-Host "  Task name  : $TASK_NAME"
Write-Host "  Script     : $BACKUP_SCRIPT"
Write-Host "  Schedule   : Daily at $TIME_24H ($TIME_12H)"
Write-Host "  Run as     : $RUN_AS_USER"
Write-Host ""

# Remove existing task if it exists
$existing = Get-ScheduledTask -TaskName $TASK_NAME -ErrorAction SilentlyContinue
if ($existing) {
    Write-Host "  Removing existing task..."
    Unregister-ScheduledTask -TaskName $TASK_NAME -Confirm:$false
}

if ($Disable) {
    Write-Host "  Scheduled task disabled/removed as requested." -ForegroundColor Yellow
    exit 0
}

# Validate the script exists
if (-not (Test-Path $BACKUP_SCRIPT)) {
    Write-Host "ERROR: Backup script not found: $BACKUP_SCRIPT" -ForegroundColor Red
    exit 1
}

# Build the action
$action = New-ScheduledTaskAction `
    -Execute "powershell.exe" `
    -Argument "-NoProfile -NonInteractive -ExecutionPolicy Bypass -File `"$BACKUP_SCRIPT`""

# Build the trigger: daily at configured time (default 7:00 PM)
$trigger = New-ScheduledTaskTrigger -Daily -At $TIME_24H

# Settings
$settings = New-ScheduledTaskSettingsSet `
    -ExecutionTimeLimit (New-TimeSpan -Hours 2) `
    -MultipleInstances IgnoreNew `
    -StartWhenAvailable `
    -RunOnlyIfNetworkAvailable:$false `
    -WakeToRun:$false

# Register the task
$registered = $false
try {
    if ($isAdmin) {
        $principal = New-ScheduledTaskPrincipal `
            -UserId $RUN_AS_USER `
            -LogonType S4U `
            -RunLevel Highest
        $task = Register-ScheduledTask `
            -TaskName $TASK_NAME `
            -Description $TASK_DESCRIPTION `
            -Action $action `
            -Trigger $trigger `
            -Settings $settings `
            -Principal $principal
        if ($task) { $registered = $true }
    }
} catch {
    Write-Host "  PowerShell registration failed ($($_)). Trying schtasks fallback..." -ForegroundColor Yellow
}

if (-not $registered) {
    $schAction = "powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File `"$BACKUP_SCRIPT`""
    & schtasks.exe /Create /F /TN $TASK_NAME /TR $schAction /SC DAILY /ST $TIME_24H | Out-Null
    if ($LASTEXITCODE -eq 0) {
        $registered = $true
    }
}

if ($registered) {
    Write-Host ""
    Write-Host "  Task registered successfully!" -ForegroundColor Green
    Write-Host ""
    if ($task) {
        Write-Host "  Name  : $($task.TaskName)"
        Write-Host "  State : $($task.State)"
    } else {
        Write-Host "  Name  : $TASK_NAME"
        Write-Host "  Mode  : Scheduled via schtasks (current user)"
    }
    Write-Host "  Next run will be at: $TIME_12H"
    Write-Host ""
    Write-Host "  To run immediately for testing:" -ForegroundColor Cyan
    Write-Host "    Start-ScheduledTask -TaskName '$TASK_NAME'"
    Write-Host ""
    Write-Host "  To verify the task in Task Scheduler UI:"
    Write-Host "    taskschd.msc"
    Write-Host ""
    Write-Host "  To view task history:"
    Write-Host "    Get-ScheduledTaskInfo -TaskName '$TASK_NAME' | Format-List"
    Write-Host ""
} else {
    Write-Host "  ERROR: Failed to register task." -ForegroundColor Red
    exit 1
}

Write-Host "=" * 60
Write-Host ""
