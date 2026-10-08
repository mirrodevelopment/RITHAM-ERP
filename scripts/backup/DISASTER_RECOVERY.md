# Ritham ERP — Disaster Recovery Guide

**System:** Ritham ERP — Tailoring & Garment Manufacturing ERP  
**Database:** ritham_erp (PostgreSQL 17)  
**Last Updated:** 2026-10-08

---

## 1. Backup Storage Architecture

```
Internal SSD (Disk 0)           External Drive
======================          ==============
C:\  PostgreSQL 17              E:\ERP-Backups\
     (data directory)              Database\Daily\
D:\  Spring Boot ERP                         ritham_erp_YYYY-MM-DD_HH-MM-SS.dump
     Source code                 Database\Weekly\
D:\  ritham-erp-uploads\            ritham_erp_YYYY-MM-DD_HH-MM-SS.dump
     migration\                  Database\Monthly\
     (OCR scan files)                ritham_erp_YYYY-MM-DD_HH-MM-SS.dump
                                  Files\Daily\
                                     migration_YYYY-MM-DD_HH-MM-SS\
                                  Logs\
                                     backup.log
```

> WARNING: C:\ and D:\ are on the SAME physical SSD. A hardware failure
> destroys both. The external drive is the only off-SSD backup.

---

## 2. Backup File Format

| File | Description |
|---|---|
| `ritham_erp_YYYY-MM-DD_HH-MM-SS.dump` | Main database backup (custom pg_dump format) |
| `ritham_erp_globals_YYYY-MM-DD_HH-MM-SS.sql` | PostgreSQL roles backup (plain SQL) |
| `Files\Daily\migration_YYYY-MM-DD_HH-MM-SS\` | OCR migration upload files |

---

## 3. Scenario: Restore Test (SAFE — No Data Loss)

Use this ANYTIME to verify a backup is usable.  
The production database is NOT affected.

**Step 1:** Connect the external drive  
**Step 2:** Open PowerShell  
**Step 3:** Run:
```powershell
powershell -ExecutionPolicy Bypass -File "d:\ritham-erp\scripts\backup\erp-restore.ps1"
```
**Step 4:** Choose option `1` (Test restore)  
**Step 5:** Select a backup file from the list  
**Step 6:** Script restores into `ritham_erp_restore_test`  
**Step 7:** Review the table and row counts shown  
**Step 8:** The test database is cleaned up automatically  

---

## 4. Scenario: Partial Recovery — Same Computer, Database Corrupted

**When to use:** The office computer works, but the database is corrupted or
data was accidentally deleted.

**Step 1:** Stop Spring Boot  
- Close the terminal running `start.bat`
- Or press Ctrl+C in the start.bat window

**Step 2:** Connect the external drive

**Step 3:** Identify the latest valid backup  
```powershell
powershell -ExecutionPolicy Bypass -File "d:\ritham-erp\scripts\backup\erp-health-check.ps1"
```

**Step 4:** Run the restore script  
```powershell
powershell -ExecutionPolicy Bypass -File "d:\ritham-erp\scripts\backup\erp-restore.ps1"
```
Choose option `2` (Production restore) — **TYPE YES to confirm twice**

**Step 5:** Wait for restore to complete (may take 1-5 minutes)

**Step 6:** Verify key data was restored (script shows table row counts)

**Step 7:** Start Spring Boot  
```
d:\ritham-erp\start.bat
```

**Step 8:** Log in at http://localhost:8080 and verify data

---

## 5. Scenario: Complete Computer Failure

**When to use:** The office computer is dead, destroyed, or unrecoverable.
This requires a new computer.

### 5.1 On the New Computer

**Step 1 — Install Windows 11 Pro**

**Step 2 — Install Java 17 (Temurin)**  
Download from: https://adoptium.net/  
Install version: OpenJDK 17 (LTS)  
Verify: `java -version` (should show 17.x)

**Step 3 — Install PostgreSQL 17**  
Download from: https://www.postgresql.org/download/windows/  
- Install version 17 (same as production)  
- Default port: 5432  
- Create superuser: `postgres`  
- Remember the postgres password (set it in pgpass.conf after install)

**Step 4 — Create the database user**  
```sql
-- Run in psql as postgres:
CREATE USER ritham WITH PASSWORD 'ritham123';
-- Change password to match your actual production password
```

**Step 5 — Install Git**  
Download from: https://git-scm.com/download/win

**Step 6 — Clone the ERP source code**  
```bash
cd D:\
git clone https://github.com/[your-repo]/ritham-erp.git
```

**Step 7 — Connect the external drive**  
Note which drive letter it gets (E:, F:, G:, etc.)

**Step 8 — Set up pgpass.conf**  
```powershell
New-Item -ItemType Directory -Path "$env:APPDATA\postgresql" -Force
# Create the file with content:
# localhost:5432:ritham_erp:ritham:YOUR_PASSWORD
# localhost:5432:*:postgres:YOUR_POSTGRES_PASSWORD
```

**Step 9 — Create the empty database**  
```sql
-- Run in psql as postgres:
CREATE DATABASE ritham_erp OWNER ritham ENCODING 'UTF8';
```

**Step 10 — Restore from backup**  
```powershell
# Update BACKUP_ROOT and PG_BIN paths in the script first if needed
powershell -ExecutionPolicy Bypass -File "D:\ritham-erp\scripts\backup\erp-restore.ps1"
```
Select option `2` (Production restore), choose the latest backup file.

**Step 11 — Restore uploaded files**  
```powershell
# Create destination
New-Item -ItemType Directory -Path "D:\ritham-erp-uploads" -Force

# Copy from backup
robocopy "E:\ERP-Backups\Files\Daily\[LATEST FOLDER]" "D:\ritham-erp-uploads" /E
```

**Step 12 — Configure application.yaml (if needed)**  
The file at `d:\ritham-erp\src\main\resources\application.yaml` uses environment
variables with sensible defaults. No change should be needed if using default credentials.

**Step 13 — Start the ERP**  
```
D:\ritham-erp\start.bat
```

**Step 14 — Verify**  
- Open: http://localhost:8080/desks/login/login.html  
- Log in with: admin / Admin@123  
- Check customers, orders, production stages

---

## 6. Manual Backup (Run Anytime)

```powershell
powershell -ExecutionPolicy Bypass -File "d:\ritham-erp\scripts\backup\erp-backup.ps1"
```

---

## 7. Check Backup Status

```powershell
powershell -ExecutionPolicy Bypass -File "d:\ritham-erp\scripts\backup\erp-health-check.ps1"
```

---

## 8. Task Scheduler (Install Once, as Administrator)

```powershell
# Run as Administrator:
powershell -ExecutionPolicy Bypass -File "d:\ritham-erp\scripts\backup\setup-task-scheduler.ps1"
```

The task runs daily at 11:00 PM.  
If the computer is off at 11 PM, the backup will run when the computer next starts.

---

## 9. If the External Drive Letter Changes

1. Open `d:\ritham-erp\scripts\backup\erp-backup.ps1`  
2. Find the CONFIG block at the top  
3. Change the one line:  
   ```powershell
   BackupRoot = "F:\ERP-Backups"   # Change E: to whatever the new drive letter is
   ```
4. Save the file  
5. Run a manual backup to verify

---

## 10. Key Paths Reference

| Item | Path |
|---|---|
| ERP source code | `D:\ritham-erp\` |
| Spring Boot starter | `D:\ritham-erp\start.bat` |
| Backup scripts | `D:\ritham-erp\scripts\backup\` |
| Backup destination | `E:\ERP-Backups\` |
| Upload files | `D:\ritham-erp-uploads\migration\` |
| pgpass.conf | `C:\Users\admin\AppData\Roaming\postgresql\pgpass.conf` |
| PostgreSQL binaries | `C:\Program Files\PostgreSQL\17\bin\` |
| PostgreSQL data | `C:\Program Files\PostgreSQL\17\data\` |
| Backup log | `E:\ERP-Backups\Logs\backup.log` |

---

## 11. Security Reminders

- The `pgpass.conf` file contains the database password. Do NOT share it.
- Backup `.dump` files contain all business data. Store the external drive securely.
- Do NOT commit `.dump` files to GitHub — they are excluded by `.gitignore`.
- The default password in `application.yaml` should be changed in production using the `DB_PASSWORD` environment variable.

---

*End of Disaster Recovery Guide*
