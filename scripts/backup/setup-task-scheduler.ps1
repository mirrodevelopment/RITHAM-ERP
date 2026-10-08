# ==============================================================================
# Ritham ERP - Task Scheduler Setup Script
# ==============================================================================
# Purpose : Registers the daily backup task in Windows Task Scheduler.
# Run     : powershell -ExecutionPolicy Bypass -File "d:\ritham-erp\scripts\backup\setup-task-scheduler.ps1"
#
# To remove the task: Unregister-ScheduledTask -TaskName "Ritham ERP - Daily DB Backup" -Confirm:$false
# ==============================================================================

$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Host "Notice: Running in standard user mode (task will run under current user)." -ForegroundColor Yellow
    Write-Host "For 24/7 background service execution without login, run as Administrator." -ForegroundColor Gray
}

# Configuration
$TASK_NAME        = "Ritham ERP - Daily DB Backup"
$TASK_DESCRIPTION = "Automatic daily backup of ritham_erp PostgreSQL database and uploaded files to external drive."
$BACKUP_SCRIPT    = "d:\ritham-erp\scripts\backup\erp-backup.ps1"
$BACKUP_HOUR      = 23   # 11 PM
$BACKUP_MINUTE    = 0
$RUN_AS_USER      = "$env:USERDOMAIN\$env:USERNAME"   # Current logged-in user

Write-Host ""
Write-Host ("=" * 60)
Write-Host "  Ritham ERP - Task Scheduler Setup"
Write-Host ("=" * 60)
Write-Host ""
Write-Host "  Task name  : $TASK_NAME"
Write-Host "  Script     : $BACKUP_SCRIPT"
Write-Host "  Schedule   : Daily at $($BACKUP_HOUR):$('{0:D2}' -f $BACKUP_MINUTE) (11:00 PM)"
Write-Host "  Run as     : $RUN_AS_USER"
Write-Host ""

# Validate the script exists
if (-not (Test-Path $BACKUP_SCRIPT)) {
    Write-Host "ERROR: Backup script not found: $BACKUP_SCRIPT" -ForegroundColor Red
    exit 1
}

# Remove existing task if it exists
$existing = Get-ScheduledTask -TaskName $TASK_NAME -ErrorAction SilentlyContinue
if ($existing) {
    Write-Host "  Removing existing task..."
    Unregister-ScheduledTask -TaskName $TASK_NAME -Confirm:$false
}

# Build the action
$action = New-ScheduledTaskAction `
    -Execute "powershell.exe" `
    -Argument "-NoProfile -NonInteractive -ExecutionPolicy Bypass -File `"$BACKUP_SCRIPT`""

# Build the trigger: daily at 11 PM
$trigger = New-ScheduledTaskTrigger -Daily -At "$($BACKUP_HOUR):$('{0:D2}' -f $BACKUP_MINUTE)"

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
    & schtasks.exe /Create /F /TN $TASK_NAME /TR $schAction /SC DAILY /ST "$($BACKUP_HOUR):$('{0:D2}' -f $BACKUP_MINUTE)" | Out-Null
    if ($LASTEXITCODE -eq 0) {
        $registered = $true
    }
}

if ($registered) {
    Write-Host ""
    Write-Host "  Task registered successfully!" -ForegroundColor Green
    Write-Host ""
    Write-Host "  Name  : $($task.TaskName)"
    Write-Host "  State : $($task.State)"
    Write-Host "  Next run will be at: 11:00 PM tonight"
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
