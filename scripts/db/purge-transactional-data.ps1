<#
.SYNOPSIS
    Purges customers, measurements, orders, order sub-entities, production tracking,
    and migration staging data while preserving schema tables, user/branch/role data,
    and production stage definitions.

.EXAMPLE
    .\scripts\db\purge-transactional-data.ps1 -Target Both -Force
#>
[CmdletBinding()]
param(
    [ValidateSet("Both", "H2", "Postgres")]
    [string]$Target = "Both",
    [string]$H2DbPath = (Join-Path (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path "data\ritham_erp"),
    [string]$PgHost = "localhost",
    [int]$PgPort = 5432,
    [string]$PgDb = "ritham_erp",
    [string]$PgUser = "ritham",
    [System.Security.SecureString]$PgPassword,
    [string]$PgBinDir = "C:\Program Files\PostgreSQL\17\bin",
    [switch]$Force
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()

if (-not $Force) {
    Write-Host "WARNING: This will permanently delete all customers, measurements, orders, payments, production tracking, and migration staging records in target: $Target" -ForegroundColor Yellow
    $confirm = Read-Host "Type PURGE to continue"
    if ($confirm -ne "PURGE") {
        Write-Host "Aborted. No database changes made." -ForegroundColor Cyan
        exit 0
    }
}

$RowCountSql = @"
SELECT 'branches' AS tbl, COUNT(*) AS rows FROM branches
UNION ALL SELECT 'roles', COUNT(*) FROM roles
UNION ALL SELECT 'departments', COUNT(*) FROM departments
UNION ALL SELECT 'employees', COUNT(*) FROM employees
UNION ALL SELECT 'refresh_tokens', COUNT(*) FROM refresh_tokens
UNION ALL SELECT 'production_stages', COUNT(*) FROM production_stages
UNION ALL SELECT 'customers', COUNT(*) FROM customers
UNION ALL SELECT 'customer_measurements', COUNT(*) FROM customer_measurements
UNION ALL SELECT 'customer_orders', COUNT(*) FROM customer_orders
UNION ALL SELECT 'order_designs', COUNT(*) FROM order_designs
UNION ALL SELECT 'order_measurements', COUNT(*) FROM order_measurements
UNION ALL SELECT 'order_materials', COUNT(*) FROM order_materials
UNION ALL SELECT 'production_tracking', COUNT(*) FROM production_tracking
UNION ALL SELECT 'order_payments', COUNT(*) FROM order_payments
UNION ALL SELECT 'order_deliveries', COUNT(*) FROM order_deliveries
UNION ALL SELECT 'migration_batches', COUNT(*) FROM migration_batches
UNION ALL SELECT 'migration_documents', COUNT(*) FROM migration_documents
UNION ALL SELECT 'migration_reviews', COUNT(*) FROM migration_reviews
UNION ALL SELECT 'migration_imports', COUNT(*) FROM migration_imports;
"@

function Get-H2JarPath {
    $h2Repo = Join-Path $env:USERPROFILE ".m2\repository\com\h2database\h2"
    if (-not (Test-Path $h2Repo)) {
        throw "H2 Maven artifact folder not found at: $h2Repo"
    }
    $jar = Get-ChildItem $h2Repo -Filter "h2-*.jar" -Recurse | Select-Object -First 1
    if (-not $jar) {
        throw "No h2-*.jar found under: $h2Repo"
    }
    return $jar.FullName
}

function Invoke-H2ShellSql {
    param(
        [string]$H2Jar,
        [string]$JdbcUrl,
        [string]$SqlStatements
    )
    $scriptInput = "$SqlStatements`nexit`n"
    $output = $scriptInput | & java -cp $H2Jar org.h2.tools.Shell -url $JdbcUrl -user sa 2>&1
    $text = $output -join "`n"
    if ($LASTEXITCODE -ne 0 -or $text -match "(?m)^Error:|Exception in thread") {
        throw "H2 execution failed:`n$text"
    }
    return $output
}

if ($Target -eq "Both" -or $Target -eq "H2") {
    $mvFile = "$H2DbPath.mv.db"
    if (-not (Test-Path $mvFile)) {
        throw "H2 database file not found: $mvFile"
    }
    $h2Jar = Get-H2JarPath
    $normalizedH2Path = $H2DbPath.Replace("\", "/")
    $h2Url = "jdbc:h2:file:${normalizedH2Path};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;IFEXISTS=TRUE"

    Write-Host "`n=== Purging H2 Dev Database ($mvFile) ===" -ForegroundColor Cyan
    $h2PurgeSql = @"
SET AUTOCOMMIT OFF;
DELETE FROM migration_imports;
DELETE FROM migration_reviews;
DELETE FROM migration_documents;
DELETE FROM migration_batches;
DELETE FROM order_deliveries;
DELETE FROM order_payments;
DELETE FROM production_tracking;
DELETE FROM order_materials;
DELETE FROM order_measurements;
DELETE FROM order_designs;
DELETE FROM customer_orders;
DELETE FROM customer_measurements;
DELETE FROM customers;
COMMIT;
ALTER TABLE customer_measurements ALTER COLUMN id RESTART WITH 1;
ALTER TABLE customer_orders ALTER COLUMN id RESTART WITH 1;
ALTER TABLE order_designs ALTER COLUMN id RESTART WITH 1;
ALTER TABLE order_measurements ALTER COLUMN id RESTART WITH 1;
ALTER TABLE order_materials ALTER COLUMN id RESTART WITH 1;
ALTER TABLE production_tracking ALTER COLUMN id RESTART WITH 1;
ALTER TABLE order_payments ALTER COLUMN id RESTART WITH 1;
ALTER TABLE order_deliveries ALTER COLUMN id RESTART WITH 1;
ALTER TABLE migration_batches ALTER COLUMN id RESTART WITH 1;
ALTER TABLE migration_documents ALTER COLUMN id RESTART WITH 1;
ALTER TABLE migration_reviews ALTER COLUMN id RESTART WITH 1;
ALTER TABLE migration_imports ALTER COLUMN id RESTART WITH 1;
ALTER SEQUENCE order_number_seq RESTART WITH 1;
"@
    $tmpH2Sql = [System.IO.Path]::ChangeExtension([System.IO.Path]::GetTempFileName(), ".sql")
    try {
        [System.IO.File]::WriteAllText($tmpH2Sql, $h2PurgeSql, [System.Text.UTF8Encoding]::new($false))
        $runOut = & java -cp $h2Jar org.h2.tools.RunScript -url $h2Url -user sa -script $tmpH2Sql 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw "H2 RunScript purge failed:`n$($runOut -join "`n")"
        }
    }
    finally {
        if (Test-Path $tmpH2Sql) { Remove-Item $tmpH2Sql -Force -ErrorAction SilentlyContinue }
    }
    Write-Host "H2 post-purge verification:" -ForegroundColor Green
    (Invoke-H2ShellSql -H2Jar $h2Jar -JdbcUrl $h2Url -SqlStatements $RowCountSql) | ForEach-Object { Write-Output $_ }
}

if ($Target -eq "Both" -or $Target -eq "Postgres") {
    $psql = Join-Path $PgBinDir "psql.exe"
    if (-not (Test-Path $psql)) {
        throw "psql.exe not found at: $psql"
    }
    $sqlFile = Join-Path $PSScriptRoot "purge-transactional-data.sql"
    if (-not (Test-Path $sqlFile)) {
        throw "Purge SQL script not found: $sqlFile"
    }

    Write-Host "`n=== Purging PostgreSQL Database ($PgDb @ ${PgHost}:${PgPort}) ===" -ForegroundColor Cyan
    $prevPw = $env:PGPASSWORD
    try {
        if ($PgPassword) {
            $env:PGPASSWORD = [System.Net.NetworkCredential]::new("", $PgPassword).Password
        }
        elseif (-not $env:PGPASSWORD -and $env:DB_PASSWORD) {
            $env:PGPASSWORD = $env:DB_PASSWORD
        }
        $psqlArgs = @("-U", $PgUser, "-h", $PgHost, "-p", "$PgPort", "-d", $PgDb, "-v", "ON_ERROR_STOP=1")
        & $psql @psqlArgs -f $sqlFile
        if ($LASTEXITCODE -ne 0) {
            throw "PostgreSQL purge failed with exit code $LASTEXITCODE"
        }
        Write-Host "PostgreSQL post-purge verification:" -ForegroundColor Green
        & $psql @psqlArgs -t -A -c $RowCountSql
        if ($LASTEXITCODE -ne 0) {
            throw "PostgreSQL verification failed with exit code $LASTEXITCODE"
        }
    }
    finally {
        $env:PGPASSWORD = $prevPw
    }
}

Write-Host "`nPurge completed cleanly." -ForegroundColor Green
