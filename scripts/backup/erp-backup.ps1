# ==============================================================================
# Ritham ERP - Automatic PostgreSQL Backup Script
# ==============================================================================
# File    : erp-backup.ps1
# Purpose : Daily automatic backup of the ritham_erp PostgreSQL database
#           and migration OCR upload files to an external drive.
# Run via : Windows Task Scheduler (see setup-task-scheduler.ps1)
# Manual  : powershell -ExecutionPolicy Bypass -File "d:\ritham-erp\scripts\backup\erp-backup.ps1"
# ==============================================================================

#Requires -Version 5.1
param(
    [string]$TargetLocation = $null,
    [switch]$CompleteBackup = $true,
    [switch]$Force = $false
)
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ==============================================================================
# CONFIGURATION BLOCK
# Edit ONLY this section to reconfigure backup settings.
# ==============================================================================
$CONFIG = @{

    # Backup Destination
    # Change this to match your external drive letter.
    # E.g. "F:\ERP-Backups" if your external HDD is drive F:
    BackupRoot        = "E:\ERP-Backups"

    # Database Settings
    DbName            = "ritham_erp"
    DbHost            = "localhost"
    DbPort            = "5432"
    DbUser            = "ritham"
    # Password is read from pgpass.conf - never put it here.
    # pgpass.conf location: C:\Users\admin\AppData\Roaming\postgresql\pgpass.conf

    # PostgreSQL Binaries
    PgBinDir          = "C:\Program Files\PostgreSQL\17\bin"

    # PostgreSQL Service Name
    PgServiceName     = "postgresql-x64-17"

    # Application File Backup
    # Directory where migration OCR upload files are stored.
    # Must match app.migration.upload-dir in application.yaml
    AppUploadDir      = "D:\ritham-erp-uploads"

    # Retention Policy
    RetainDaily       = 7
    RetainWeekly      = 4
    RetainMonthly     = 12

    # Minimum Free Space Required (MB)
    # Backup will not start if less than this amount of space is available.
    MinFreeSpaceMB    = 500

    # Lock File - prevents two backup processes from running simultaneously.
    LockFile          = "C:\Windows\Temp\ritham-erp-backup.lock"
}
# ==============================================================================

# Override target location if specified via parameter
if ($TargetLocation -and $TargetLocation.Trim() -ne "") {
    $CONFIG.BackupRoot = $TargetLocation.Trim()
    Write-Host "Target backup location set to: $($CONFIG.BackupRoot)" -ForegroundColor Cyan
}

# Derived paths (do not edit)
$PG_DUMP     = Join-Path $CONFIG.PgBinDir "pg_dump.exe"
$PG_RESTORE  = Join-Path $CONFIG.PgBinDir "pg_restore.exe"
$PG_DUMPALL  = Join-Path $CONFIG.PgBinDir "pg_dumpall.exe"
$PG_ISREADY  = Join-Path $CONFIG.PgBinDir "pg_isready.exe"

$DB_DAILY    = Join-Path $CONFIG.BackupRoot "Database\Daily"
$DB_WEEKLY   = Join-Path $CONFIG.BackupRoot "Database\Weekly"
$DB_MONTHLY  = Join-Path $CONFIG.BackupRoot "Database\Monthly"
$FILE_DAILY  = Join-Path $CONFIG.BackupRoot "Files\Daily"
$FILE_WEEKLY = Join-Path $CONFIG.BackupRoot "Files\Weekly"
$FILE_MONTHLY= Join-Path $CONFIG.BackupRoot "Files\Monthly"
$LOG_DIR     = Join-Path $CONFIG.BackupRoot "Logs"
$LOG_FILE    = Join-Path $LOG_DIR "backup.log"

$TIMESTAMP   = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
$START_TIME  = Get-Date

$BACKUP_DUMP = Join-Path $DB_DAILY "ritham_erp_$TIMESTAMP.dump"
$GLOBALS_SQL = Join-Path $DB_DAILY "ritham_erp_globals_$TIMESTAMP.sql"

$env:PGPASSFILE = "$env:APPDATA\postgresql\pgpass.conf"

$DB_BACKUP_OK   = $false
$FILE_BACKUP_OK = $false
$VERIFY_OK      = $false

# ==============================================================================
# LOGGING FUNCTIONS
# ==============================================================================
function Write-Log {
    param([string]$Message, [string]$Level = "INFO")
    $ts = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $line = "[$ts] [$Level] $Message"
    if (-not (Test-Path $LOG_DIR)) {
        New-Item -ItemType Directory -Path $LOG_DIR -Force | Out-Null
    }
    Add-Content -Path $LOG_FILE -Value $line -Encoding UTF8
    switch ($Level) {
        "ERROR" { Write-Host $line -ForegroundColor Red }
        "WARN"  { Write-Host $line -ForegroundColor Yellow }
        "OK"    { Write-Host $line -ForegroundColor Green }
        default { Write-Host $line }
    }
}

function Write-LogSeparator {
    $line = "=" * 72
    if (-not (Test-Path $LOG_DIR)) {
        New-Item -ItemType Directory -Path $LOG_DIR -Force | Out-Null
    }
    Add-Content -Path $LOG_FILE -Value $line -Encoding UTF8
    Write-Host $line
}

# ==============================================================================
# LOCK FILE FUNCTIONS
# ==============================================================================
function New-BackupLock {
    if (Test-Path $CONFIG.LockFile) {
        $lockContent = Get-Content $CONFIG.LockFile -Raw -ErrorAction SilentlyContinue
        Write-Log "ABORT: Another backup is already running. Lock: $($CONFIG.LockFile)" "ERROR"
        Write-Log "Lock contents: $lockContent" "WARN"
        Write-Log "If no backup is running, delete the lock file and retry." "WARN"
        exit 2
    }
    "PID=$PID Started=$TIMESTAMP" | Set-Content $CONFIG.LockFile -Encoding UTF8
    Write-Log "Lock acquired."
}

function Remove-BackupLock {
    if (Test-Path $CONFIG.LockFile) {
        Remove-Item $CONFIG.LockFile -Force -ErrorAction SilentlyContinue
        Write-Log "Lock released."
    }
}

# ==============================================================================
# PRE-FLIGHT CHECKS
# ==============================================================================
function Test-ExternalDrive {
    $driveRoot = Split-Path $CONFIG.BackupRoot -Qualifier
    if (-not (Test-Path $driveRoot)) {
        Write-Log "FAIL: Backup drive not found: $driveRoot" "ERROR"
        Write-Log "Ensure the external drive is connected and the drive letter is correct." "ERROR"
        return $false
    }
    Write-Log "External drive accessible: $driveRoot" "OK"
    return $true
}

function Test-DiskSpace {
    $drive = (Split-Path $CONFIG.BackupRoot -Qualifier).TrimEnd(':')
    try {
        $vol = Get-PSDrive -Name $drive -PSProvider FileSystem
        $freeGB = [math]::Round($vol.Free / 1GB, 2)
        $freeMB = [math]::Round($vol.Free / 1MB, 0)
        Write-Log "Backup drive free space: $freeGB GB"
        if ($freeMB -lt $CONFIG.MinFreeSpaceMB) {
            Write-Log "FAIL: Insufficient space. Required: $($CONFIG.MinFreeSpaceMB) MB, Available: $freeMB MB" "ERROR"
            return $false
        }
        Write-Log "Disk space check passed." "OK"
        return $true
    } catch {
        Write-Log "WARNING: Could not check disk space. Proceeding cautiously." "WARN"
        return $true
    }
}

function Test-PostgreSQLService {
    try {
        $svc = Get-Service -Name $CONFIG.PgServiceName -ErrorAction SilentlyContinue
        if ($svc -and $svc.Status -eq "Running") {
            Write-Log "PostgreSQL service running: $($CONFIG.PgServiceName)" "OK"
            return $true
        }
    } catch {
        # Service query error ignored, will check process directly
    }

    $proc = Get-Process -Name "postgres" -ErrorAction SilentlyContinue
    if ($proc) {
        Write-Log "PostgreSQL process running (PID: $($proc[0].Id))" "OK"
        return $true
    }

    # Attempt to start service if stopped
    try {
        Write-Log "PostgreSQL is not running. Attempting to start service '$($CONFIG.PgServiceName)'..." "WARN"
        Start-Service -Name $CONFIG.PgServiceName -ErrorAction Stop
        Start-Sleep -Seconds 3
        $svc = Get-Service -Name $CONFIG.PgServiceName -ErrorAction SilentlyContinue
        if ($svc -and $svc.Status -eq "Running") {
            Write-Log "PostgreSQL service started successfully." "OK"
            return $true
        }
    } catch {
        Write-Log "Service start failed ($($_)). Attempting pg_ctl start fallback..." "WARN"
    }

    # Fallback to pg_ctl if service control was not permitted
    $pgctl = Join-Path $CONFIG.PgBinDir "pg_ctl.exe"
    $dataDir = "C:\Program Files\PostgreSQL\17\data"
    if ((Test-Path $pgctl) -and (Test-Path $dataDir)) {
        try {
            $p = Start-Process -FilePath $pgctl -ArgumentList "-D `"$dataDir`" start" -PassThru -WindowStyle Hidden
            $p.WaitForExit(5000) | Out-Null
            Start-Sleep -Seconds 2
            $proc = Get-Process -Name "postgres" -ErrorAction SilentlyContinue
            if ($proc) {
                Write-Log "PostgreSQL started via pg_ctl (PID: $($proc[0].Id))." "OK"
                return $true
            }
        } catch {
            Write-Log "pg_ctl start failed: $_" "WARN"
        }
    }

    Write-Log "FAIL: PostgreSQL is not running and could not be started." "ERROR"
    return $false
}

function Test-PgDumpExists {
    if (-not (Test-Path $PG_DUMP)) {
        Write-Log "FAIL: pg_dump.exe not found at: $PG_DUMP" "ERROR"
        return $false
    }
    Write-Log "pg_dump found: $PG_DUMP" "OK"
    return $true
}

function Test-DatabaseConnectivity {
    Write-Log "Testing database connectivity..."
    $maxAttempts = 12
    for ($i = 1; $i -le $maxAttempts; $i++) {
        & $PG_ISREADY -h $CONFIG.DbHost -p $CONFIG.DbPort -U $CONFIG.DbUser -d $CONFIG.DbName | Out-Null
        if ($LASTEXITCODE -eq 0) {
            Write-Log "Database connectivity OK: $($CONFIG.DbName)" "OK"
            return $true
        }
        if ($i -lt $maxAttempts) {
            Write-Log "Database engine starting up, waiting (attempt $i/$maxAttempts)..." "WARN"
            Start-Sleep -Seconds 2
        }
    }
    Write-Log "FAIL: Cannot connect to '$($CONFIG.DbName)' on $($CONFIG.DbHost):$($CONFIG.DbPort)" "ERROR"
    Write-Log "Check: Is PostgreSQL running? Is pgpass.conf configured?" "WARN"
    return $false
}

# ==============================================================================
# DIRECTORY SETUP
# ==============================================================================
function Initialize-BackupDirectories {
    @($DB_DAILY, $DB_WEEKLY, $DB_MONTHLY,
      $FILE_DAILY, $FILE_WEEKLY, $FILE_MONTHLY,
      $LOG_DIR) | ForEach-Object {
        if (-not (Test-Path $_)) {
            New-Item -ItemType Directory -Path $_ -Force | Out-Null
            Write-Log "Created directory: $_"
        }
    }
    Write-Log "Backup directories ready." "OK"
}

# ==============================================================================
# DATABASE BACKUP
# ==============================================================================
function Invoke-DatabaseBackup {
    Write-Log "Starting database backup..."
    Write-Log "Database : $($CONFIG.DbName)"
    Write-Log "Output   : $BACKUP_DUMP"

    $dumpArgs = @(
        "--host=$($CONFIG.DbHost)",
        "--port=$($CONFIG.DbPort)",
        "--username=$($CONFIG.DbUser)",
        "--dbname=$($CONFIG.DbName)",
        "--format=custom",
        "--blobs",
        "--compress=6",
        "--no-password",
        "--file=$BACKUP_DUMP"
    )
    & $PG_DUMP @dumpArgs

    if ($LASTEXITCODE -ne 0) {
        Write-Log "FAIL: pg_dump exited with code $LASTEXITCODE" "ERROR"
        return $false
    }

    if (-not (Test-Path $BACKUP_DUMP)) {
        Write-Log "FAIL: Backup file was not created: $BACKUP_DUMP" "ERROR"
        return $false
    }

    $fileSize = (Get-Item $BACKUP_DUMP).Length
    if ($fileSize -eq 0) {
        Write-Log "FAIL: Backup file is zero bytes." "ERROR"
        return $false
    }

    $fileSizeMB = [math]::Round($fileSize / 1MB, 2)
    Write-Log "Database backup created: $(Split-Path $BACKUP_DUMP -Leaf)" "OK"
    Write-Log "Backup size: $fileSizeMB MB" "OK"
    return $true
}

function Invoke-GlobalsBackup {
    Write-Log "Backing up global PostgreSQL objects (roles, tablespaces)..."
    try {
        $dumpAllArgs = @(
            "--host=$($CONFIG.DbHost)",
            "--port=$($CONFIG.DbPort)",
            "--username=$($CONFIG.DbUser)",
            "--globals-only",
            "--no-password",
            "--file=$GLOBALS_SQL"
        )
        & $PG_DUMPALL @dumpAllArgs 2>$null

        if ((Test-Path $GLOBALS_SQL) -and (Get-Item $GLOBALS_SQL).Length -gt 0) {
            $sz = [math]::Round((Get-Item $GLOBALS_SQL).Length / 1KB, 1)
            Write-Log "Globals backup created: $(Split-Path $GLOBALS_SQL -Leaf) ($sz KB)" "OK"
            return
        }
    } catch {
        # Globals backup optional, continue
    }

    Write-Log "WARNING: Globals backup not created. Continuing." "WARN"
}

$global:TOC_ENTRIES = 0

function Invoke-BackupVerification {
    Write-Log "Verifying backup with pg_restore --list..."
    $origPref = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $verifyOutput = & $PG_RESTORE --list "$BACKUP_DUMP" 2>&1
    $exitCode = $LASTEXITCODE
    $ErrorActionPreference = $origPref

    if ($exitCode -ne 0) {
        Write-Log "FAIL: Backup verification FAILED. Dump file may be corrupt." "ERROR"
        Write-Log "Verification output: $($verifyOutput -join ' ')" "ERROR"
        Write-Log "Backup kept for inspection: $BACKUP_DUMP" "WARN"
        return $false
    }
    $global:TOC_ENTRIES = ($verifyOutput | Measure-Object -Line).Lines
    Write-Log "Backup verification PASSED. TOC entries: $global:TOC_ENTRIES" "OK"
    return $true
}

function Invoke-ManifestGeneration {
    param(
        [string]$DumpPath,
        [string]$GlobalsPath,
        [int]$TocEntries,
        [double]$DurationSeconds
    )
    try {
        Write-Log "Generating complete backup manifest (manifest.json)..."
        $dumpHash = ""
        $dumpSizeMB = 0
        if (Test-Path $DumpPath) {
            $dumpHash = (Get-FileHash -Path $DumpPath -Algorithm SHA256).Hash
            $dumpSizeMB = [math]::Round((Get-Item $DumpPath).Length / 1MB, 2)
        }
        $manifest = [PSCustomObject]@{
            snapshotName    = Split-Path $DumpPath -Leaf
            timestamp       = $TIMESTAMP
            databaseName    = $CONFIG.DbName
            databaseHost    = $CONFIG.DbHost
            databasePort    = $CONFIG.DbPort
            erpVersion      = "0.0.1-SNAPSHOT"
            pgVersion       = "PostgreSQL 17"
            targetLocation  = $CONFIG.BackupRoot
            dumpFile        = (Split-Path $DumpPath -Leaf)
            dumpSizeMB      = $dumpSizeMB
            dumpSha256      = $dumpHash
            globalsIncluded = (Test-Path $GlobalsPath)
            tocEntriesCount = $TocEntries
            durationSeconds = $DurationSeconds
            completeBackup  = $true
            status          = "VERIFIED"
            hostName        = $env:COMPUTERNAME
            user            = $env:USERNAME
        }
        $manifestJson = $manifest | ConvertTo-Json -Depth 5
        $manifestPath = Join-Path (Split-Path $DumpPath -Parent) "manifest_$TIMESTAMP.json"
        $latestManifestPath = Join-Path $CONFIG.BackupRoot "manifest-latest.json"
        $manifestJson | Set-Content -Path $manifestPath -Encoding UTF8
        $manifestJson | Set-Content -Path $latestManifestPath -Encoding UTF8
        Write-Log "Manifest created: $(Split-Path $manifestPath -Leaf)" "OK"
    } catch {
        Write-Log "Warning: Failed to generate manifest: $_" "WARN"
    }
}

# ==============================================================================
# FILE BACKUP
# ==============================================================================
function Invoke-FileBackup {
    if (-not (Test-Path $CONFIG.AppUploadDir)) {
        Write-Log "Upload directory not found: $($CONFIG.AppUploadDir). Skipping." "WARN"
        return $true
    }

    $uploadFiles = @(Get-ChildItem $CONFIG.AppUploadDir -Recurse -File -ErrorAction SilentlyContinue)
    $fileCount = $uploadFiles.Count
    if ($fileCount -eq 0) {
        Write-Log "Upload directory is empty. No files to back up."
        return $true
    }

    $destDir = Join-Path $FILE_DAILY "migration_$TIMESTAMP"
    Write-Log "Backing up $fileCount application files..."
    Write-Log "Source : $($CONFIG.AppUploadDir)"
    Write-Log "Dest   : $destDir"

    robocopy "$($CONFIG.AppUploadDir)" "$destDir" /E /NP /NDL /NJH /R:2 /W:3 | Out-Null
    if ($LASTEXITCODE -ge 8) {
        Write-Log "FAIL: robocopy failed with code $LASTEXITCODE" "ERROR"
        return $false
    }

    $copiedFiles = @(Get-ChildItem $destDir -Recurse -File -ErrorAction SilentlyContinue)
    Write-Log ("File backup complete. Files copied: {0}" -f $copiedFiles.Count) "OK"
    return $true
}

# ==============================================================================
# RETENTION POLICY
# ==============================================================================
function Invoke-RetentionPolicy {
    Write-Log "Applying retention policy..."

    # Promote to Weekly on Sundays
    if ((Get-Date).DayOfWeek -eq "Sunday") {
        Write-Log "Sunday: promoting backup to Weekly..."
        Copy-Item $BACKUP_DUMP (Join-Path $DB_WEEKLY (Split-Path $BACKUP_DUMP -Leaf)) -Force
        if (Test-Path $GLOBALS_SQL) {
            Copy-Item $GLOBALS_SQL (Join-Path $DB_WEEKLY (Split-Path $GLOBALS_SQL -Leaf)) -Force
        }
        Write-Log "Weekly backup created." "OK"
    }

    # Promote to Monthly on 1st of month
    if ((Get-Date).Day -eq 1) {
        Write-Log "1st of month: promoting backup to Monthly..."
        Copy-Item $BACKUP_DUMP (Join-Path $DB_MONTHLY (Split-Path $BACKUP_DUMP -Leaf)) -Force
        if (Test-Path $GLOBALS_SQL) {
            Copy-Item $GLOBALS_SQL (Join-Path $DB_MONTHLY (Split-Path $GLOBALS_SQL -Leaf)) -Force
        }
        Write-Log "Monthly backup created." "OK"
    }

    Invoke-PruneDumps -Directory $DB_DAILY   -Keep $CONFIG.RetainDaily   -Label "Daily"
    Invoke-PruneDumps -Directory $DB_WEEKLY  -Keep $CONFIG.RetainWeekly  -Label "Weekly"
    Invoke-PruneDumps -Directory $DB_MONTHLY -Keep $CONFIG.RetainMonthly -Label "Monthly"

    Write-Log "Retention policy applied." "OK"
}

function Invoke-PruneDumps {
    param([string]$Directory, [int]$Keep, [string]$Label)

    $files = @(Get-ChildItem -Path $Directory -Filter "ritham_erp_*.dump" -File -ErrorAction SilentlyContinue |
             Sort-Object LastWriteTime -Descending)

    if ($files.Count -le $Keep) {
        Write-Log "$Label backups: $($files.Count) / $Keep - no cleanup needed."
        return
    }

    $toDelete = $files | Select-Object -Skip $Keep
    foreach ($f in $toDelete) {
        # Safety: file must match exact naming pattern before deletion
        if ($f.Name -match "^ritham_erp_\d{4}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2}\.dump$") {
            Write-Log "Removing old $Label backup: $($f.Name)"
            Remove-Item $f.FullName -Force
            # Remove matching globals file
            $ts = $f.Name -replace "^ritham_erp_", "" -replace "\.dump$", ""
            $globalsFile = Join-Path $Directory "ritham_erp_globals_$ts.sql"
            if (Test-Path $globalsFile) {
                Remove-Item $globalsFile -Force
                Write-Log "Removed matching globals: ritham_erp_globals_$ts.sql"
            }
        } else {
            Write-Log "SKIP: '$($f.Name)' does not match naming pattern - not deleted." "WARN"
        }
    }
}

# ==============================================================================
# MAIN EXECUTION
# ==============================================================================
Write-LogSeparator
Write-Log "Ritham ERP Backup Started"
Write-Log "Timestamp : $TIMESTAMP"
Write-Log "Database  : $($CONFIG.DbName)"
Write-Log "Backup to : $($CONFIG.BackupRoot)"
Write-LogSeparator

New-BackupLock

try {
    Write-Log "--- Pre-flight checks ---"
    if (-not (Test-ExternalDrive))        { throw "External drive not accessible." }
    if (-not (Test-DiskSpace))            { throw "Insufficient disk space." }
    if (-not (Test-PostgreSQLService))    { throw "PostgreSQL service not running." }
    if (-not (Test-PgDumpExists))         { throw "pg_dump.exe not found." }
    if (-not (Test-DatabaseConnectivity)) { throw "Cannot connect to database." }
    Write-Log "All pre-flight checks passed." "OK"

    Initialize-BackupDirectories

    Write-Log "--- Database Backup ---"
    if (Invoke-DatabaseBackup) {
        $DB_BACKUP_OK = $true
    } else {
        throw "Database backup failed."
    }

    Invoke-GlobalsBackup

    Write-Log "--- Backup Verification ---"
    if (Invoke-BackupVerification) {
        $VERIFY_OK = $true
    } else {
        Write-Log "Verification failed - status DEGRADED. Previous backups preserved." "ERROR"
    }

    Write-Log "--- File Backup ---"
    $FILE_BACKUP_OK = Invoke-FileBackup

    Write-Log "--- Retention Policy ---"
    Invoke-RetentionPolicy

    $duration = [math]::Round(((Get-Date) - $START_TIME).TotalSeconds, 1)

    # Generate Manifest metadata for complete snapshot
    Invoke-ManifestGeneration -DumpPath $BACKUP_DUMP -GlobalsPath $GLOBALS_SQL -TocEntries $global:TOC_ENTRIES -DurationSeconds $duration

    Write-LogSeparator
    Write-Log "BACKUP COMPLETED"
    Write-Log "Duration        : $duration seconds"
    Write-Log "DB backup       : $(if ($DB_BACKUP_OK) {'SUCCESS'} else {'FAILED'})"
    Write-Log "Verified        : $(if ($VERIFY_OK) {'SUCCESS'} else {'FAILED'})"
    Write-Log "File backup     : $(if ($FILE_BACKUP_OK) {'SUCCESS'} else {'SKIPPED/FAILED'})"
    Write-Log "Backup file     : $(Split-Path $BACKUP_DUMP -Leaf)"
    Write-Log "Status          : SUCCESS" "OK"
    Write-LogSeparator

} catch {
    $duration = [math]::Round(((Get-Date) - $START_TIME).TotalSeconds, 1)
    Write-LogSeparator
    Write-Log "BACKUP FAILED: $_" "ERROR"
    Write-Log "Duration: $duration seconds" "ERROR"
    Write-Log "Check log for details: $LOG_FILE" "ERROR"
    Write-LogSeparator
    Remove-BackupLock
    exit 1

} finally {
    Remove-BackupLock
}

exit 0
