# ==============================================================================
# Ritham ERP - Database Restore Script
# ==============================================================================
# Purpose : Restores a database backup, either to a TEST database (safe)
#           or to the PRODUCTION database (full disaster recovery).
#
# IMPORTANT: Run this ONLY when instructed during recovery.
#            NEVER run the production restore during normal operations.
#
# Run:  powershell -ExecutionPolicy Bypass -File "d:\ritham-erp\scripts\backup\erp-restore.ps1"
# ==============================================================================

[CmdletBinding()]
param(
    [string]$TargetLocation = $null,
    [string]$BackupFile     = $null,
    [string]$Mode           = "TEST",
    [switch]$NonInteractive,
    [switch]$Force
)

$PG_BIN        = "C:\Program Files\PostgreSQL\17\bin"
$PG_RESTORE    = Join-Path $PG_BIN "pg_restore.exe"
$PSQL          = Join-Path $PG_BIN "psql.exe"
$BACKUP_ROOT   = "E:\ERP-Backups"

if ($TargetLocation -and $TargetLocation.Trim() -ne "") {
    $BACKUP_ROOT = $TargetLocation.Trim()
    Write-Host "Target backup repository set to: $BACKUP_ROOT" -ForegroundColor Cyan
}

$DB_DAILY      = Join-Path $BACKUP_ROOT "Database\Daily"
$DB_WEEKLY     = Join-Path $BACKUP_ROOT "Database\Weekly"
$DB_MONTHLY    = Join-Path $BACKUP_ROOT "Database\Monthly"
$DB_UPLOADS    = Join-Path $BACKUP_ROOT "Uploads"

$DB_HOST       = "localhost"
$DB_PORT       = "5432"
$DB_USER       = "ritham"
$PROD_DB       = "ritham_erp"
$TEST_DB       = "ritham_erp_restore_test"

$env:PGPASSFILE = "$env:APPDATA\postgresql\pgpass.conf"
$env:PAGER      = ""
if (-not $env:PGPASSWORD) {
    $env:PGPASSWORD = "ritham123"
}

function Write-Title { param([string]$t) Write-Host ""; Write-Host ("=" * 60) -ForegroundColor Cyan; Write-Host "  $t" -ForegroundColor Cyan; Write-Host ("=" * 60) -ForegroundColor Cyan }
function Confirm-UserAction { param([string]$prompt) Write-Host ""; Write-Host $prompt -ForegroundColor Yellow; $answer = Read-Host "  Type YES to continue, or anything else to abort"; if ($answer -ne "YES") { Write-Host "Aborted by user." -ForegroundColor Red; exit 0 } }

Write-Title "Ritham ERP - Database Restore"
Write-Host "  This script restores a database backup." -ForegroundColor Gray
Write-Host "  A TEST restore (safe) or PRODUCTION restore can be performed." -ForegroundColor Gray

# --- Mode selection ---
if ($NonInteractive) {
    if ($Mode -eq "2" -or $Mode -ieq "PRODUCTION") {
        Write-Host ""
        Write-Host "[DANGER] NON-INTERACTIVE PRODUCTION RESTORE SELECTED" -ForegroundColor Red
        if (-not $Force) {
            Write-Host "Error: Non-interactive production restore requires -Force switch." -ForegroundColor Red
            exit 1
        }
        $TARGET_DB = $PROD_DB
        $IS_PRODUCTION = $true
    } else {
        Write-Host ""
        Write-Host "[SAFE] Non-interactive Test restore into '$TEST_DB'" -ForegroundColor Green
        $TARGET_DB = $TEST_DB
        $IS_PRODUCTION = $false
    }
} else {
    Write-Host ""
    Write-Host "  Select restore mode:" -ForegroundColor Cyan
    Write-Host "  1. TEST restore   - restores into '$TEST_DB' (SAFE, does not affect production)" -ForegroundColor Green
    Write-Host "  2. FULL restore   - restores into '$PROD_DB' (DANGER - overwrites production!)" -ForegroundColor Red
    Write-Host ""
    $modeInput = Read-Host "  Enter 1 or 2"

    if ($modeInput -eq "2") {
        Write-Host ""
        Write-Host "[DANGER] PRODUCTION RESTORE SELECTED" -ForegroundColor Red
        Write-Host "This will OVERWRITE all data in '$PROD_DB'." -ForegroundColor Red
        Write-Host "Ensure Spring Boot is STOPPED before continuing." -ForegroundColor Red
        Confirm-UserAction "Type YES if Spring Boot is stopped and you want to overwrite production:"
        $TARGET_DB = $PROD_DB
        $IS_PRODUCTION = $true
    } else {
        Write-Host ""
        Write-Host "[SAFE] Test restore into '$TEST_DB'" -ForegroundColor Green
        $TARGET_DB = $TEST_DB
        $IS_PRODUCTION = $false
    }
}

# --- Select backup file ---
Write-Title "Select Backup File"

if ($BackupFile -and (Test-Path $BackupFile)) {
    $BACKUP_FILE = (Resolve-Path $BackupFile).Path
    Write-Host "  Using specified backup file: $BACKUP_FILE" -ForegroundColor Green
} elseif ($NonInteractive) {
    Write-Host "  Error: -BackupFile parameter is missing or file does not exist: '$BackupFile'" -ForegroundColor Red
    exit 1
} else {
    $allDumps = @()
    foreach ($dir in @($DB_DAILY, $DB_WEEKLY, $DB_MONTHLY, $DB_UPLOADS)) {
        if (Test-Path $dir) {
            $allDumps += Get-ChildItem -Path $dir -Filter "*.dump" -File
        }
    }

    if ($allDumps.Count -eq 0) {
        Write-Host "  No backup files found in $BACKUP_ROOT" -ForegroundColor Red
        exit 1
    }

    $sortedDumps = $allDumps | Sort-Object LastWriteTime -Descending | Select-Object -First 20
    Write-Host "  Available backups (most recent first):" -ForegroundColor Cyan
    Write-Host ""
    for ($i = 0; $i -lt $sortedDumps.Count; $i++) {
        $f = $sortedDumps[$i]
        $sizeMB = [math]::Round($f.Length / 1MB, 2)
        $age = [math]::Round(((Get-Date) - $f.LastWriteTime).TotalHours, 1)
        Write-Host ("  [{0,2}] {1}  ({2} MB, {3}h ago)" -f ($i+1), $f.Name, $sizeMB, $age)
    }

    Write-Host ""
    $choice = Read-Host "  Enter number of backup to restore (1-$($sortedDumps.Count))"
    $idx = [int]$choice - 1
    if ($idx -lt 0 -or $idx -ge $sortedDumps.Count) {
        Write-Host "Invalid selection. Aborted." -ForegroundColor Red
        exit 1
    }
    $BACKUP_FILE = $sortedDumps[$idx].FullName
    Write-Host ""
    Write-Host "  Selected: $BACKUP_FILE" -ForegroundColor Green
}

# --- Verify backup file is readable ---
Write-Title "Pre-Restore Verification"
Write-Host "  Verifying backup file with pg_restore --list..."
$listOutput = & $PG_RESTORE --list "$BACKUP_FILE" 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "  FAIL: Backup file cannot be read by pg_restore." -ForegroundColor Red
    Write-Host "  $($listOutput -join ' ')" -ForegroundColor Red
    Write-Host "  Select a different backup file." -ForegroundColor Yellow
    exit 1
}
$tocCount = ($listOutput | Measure-Object -Line).Lines
Write-Host "  Backup file is valid. TOC entries: $tocCount" -ForegroundColor Green

# --- Create / recreate target database ---
Write-Title "Preparing Target Database: $TARGET_DB"

if ($IS_PRODUCTION) {
    if (-not $NonInteractive) {
        Confirm-UserAction "FINAL WARNING: This will DROP and recreate '$PROD_DB'. All current data will be LOST. Type YES:"
    }
    Write-Host "  Dropping and recreating '$PROD_DB'..."
    & $PSQL -h $DB_HOST -p $DB_PORT -U $DB_USER -d postgres --pset=pager=off -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$PROD_DB' AND pid <> pg_backend_pid();" | Out-Null
    & $PSQL -h $DB_HOST -p $DB_PORT -U $DB_USER -d postgres --pset=pager=off -c "DROP DATABASE IF EXISTS $PROD_DB;" | Out-Null
    & $PSQL -h $DB_HOST -p $DB_PORT -U $DB_USER -d postgres --pset=pager=off -c "CREATE DATABASE $PROD_DB OWNER $DB_USER ENCODING 'UTF8';" | Out-Null
} else {
    Write-Host "  Dropping existing test database '$TEST_DB' if it exists..."
    & $PSQL -h $DB_HOST -p $DB_PORT -U $DB_USER -d postgres --pset=pager=off -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$TEST_DB' AND pid <> pg_backend_pid();" | Out-Null
    & $PSQL -h $DB_HOST -p $DB_PORT -U $DB_USER -d postgres --pset=pager=off -c "DROP DATABASE IF EXISTS $TEST_DB;" | Out-Null
    & $PSQL -h $DB_HOST -p $DB_PORT -U $DB_USER -d postgres --pset=pager=off -c "CREATE DATABASE $TEST_DB OWNER $DB_USER ENCODING 'UTF8';" | Out-Null
    Write-Host "  Test database '$TEST_DB' created." -ForegroundColor Green
}

# --- Restore ---
Write-Title "Restoring Database"
Write-Host "  Restoring '$BACKUP_FILE' into '$TARGET_DB'..."
Write-Host "  This may take several minutes..." -ForegroundColor Gray

$restoreArgs = @(
    "--host=$DB_HOST",
    "--port=$DB_PORT",
    "--username=$DB_USER",
    "--dbname=$TARGET_DB",
    "--no-password",
    "--verbose",
    $BACKUP_FILE
)
& $PG_RESTORE @restoreArgs 2>&1 | Where-Object { $_ -match "^(processing|restoring|creating|setting)" } | ForEach-Object { Write-Host "  $_" -ForegroundColor Gray }

if ($LASTEXITCODE -gt 1) {
    # Exit code 1 means warnings (acceptable), >1 means errors
    Write-Host "  pg_restore completed with errors (code $LASTEXITCODE). Check output above." -ForegroundColor Red
    exit $LASTEXITCODE
} else {
    Write-Host "  Restore completed successfully." -ForegroundColor Green
}

# --- Verification ---
Write-Title "Post-Restore Verification"
Write-Host "  Checking table counts..." -ForegroundColor Gray

$env:PAGER = ""
$tableQuery = "SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename;"
$tableOutput = & $PSQL -h $DB_HOST -p $DB_PORT -U $DB_USER -d $TARGET_DB --pset=pager=off -c $tableQuery 2>&1
Write-Host ($tableOutput -join "`n") -ForegroundColor Gray

$countQuery = "SELECT 'customers' AS tbl, COUNT(*) FROM customers UNION ALL SELECT 'customer_orders', COUNT(*) FROM customer_orders UNION ALL SELECT 'employees', COUNT(*) FROM employees UNION ALL SELECT 'production_stages', COUNT(*) FROM production_stages;"
Write-Host ""
Write-Host "  Key table row counts:" -ForegroundColor Cyan
& $PSQL -h $DB_HOST -p $DB_PORT -U $DB_USER -d $TARGET_DB --pset=pager=off -c $countQuery

# Check sequences
Write-Host ""
Write-Host "  Sequences:" -ForegroundColor Cyan
& $PSQL -h $DB_HOST -p $DB_PORT -U $DB_USER -d $TARGET_DB --pset=pager=off -c "SELECT sequencename, last_value FROM pg_sequences WHERE schemaname = 'public' ORDER BY sequencename;"

if (-not $IS_PRODUCTION) {
    Write-Title "Test Restore Complete"
    Write-Host "  The test database '$TEST_DB' has been restored and verified." -ForegroundColor Green
    Write-Host "  Production database '$PROD_DB' was NOT affected." -ForegroundColor Green
    Write-Host ""
    Write-Host "  To clean up the test database, run:" -ForegroundColor Gray
    Write-Host "  psql -U postgres -d postgres -c 'DROP DATABASE $TEST_DB;'" -ForegroundColor Gray
} else {
    Write-Title "Production Restore Complete"
    Write-Host "  '$PROD_DB' has been restored." -ForegroundColor Green
    Write-Host "  You may now start the Spring Boot application via start.bat" -ForegroundColor Green
}

Write-Host ""
