# Ritham ERP — Production Deployment & Cloudflare Tunnel Architecture Guide

This guide details the complete production architecture, deployment configuration, DNS setup, security hardening, and operational procedures for **Ritham ERP**.

---

## 1. Production Architecture Overview

The system operates in a hybrid edge-and-office architecture designed for high availability, zero cloud database egress costs, and absolute database privacy:

```
                         Internet (HTTPS Only)
┌────────────────────────────────────────────────────────────────────────┐
│  End Users (Browsers, Mobile & Office Desktops)                        │
│  https://mirro.in  /  https://www.mirro.in                             │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
                                   ▼
┌────────────────────────────────────────────────────────────────────────┐
│  GitHub Pages Edge CDN                                                 │
│  - Hosts Vanilla HTML5/CSS/JavaScript single-page desks                │
│  - Custom domain: mirro.in / www.mirro.in with Enforced HTTPS          │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
                                   │ API Requests (REST / JSON)
                                   ▼
┌────────────────────────────────────────────────────────────────────────┐
│  Cloudflare Edge CDN                                                   │
│  - Custom domain: https://api.mirro.in                                 │
│  - SSL Termination: Full (Strict) with Cloudflare DDoS & WAF           │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
                                   │ Cloudflare Tunnel (Outbound Encrypted QUIC/TCP)
                                   │ [ZERO INBOUND PORTS OPENED ON OFFICE ROUTER]
                                   ▼
┌────────────────────────────────────────────────────────────────────────┐
│  Office / Local Host Computer (Windows)                                │
│                                                                        │
│   ┌────────────────────────────────────────────────────────────────┐   │
│   │ cloudflared Windows Service                                    │   │
│   │ - Proxies api.mirro.in -> http://localhost:8080                 │   │
│   └──────────────────────────────┬─────────────────────────────────┘   │
│                                  │ Local Loopback                      │
│                                  ▼                                     │
│   ┌────────────────────────────────────────────────────────────────┐   │
│   │ Spring Boot Application (Profile: prod)                        │   │
│   │ - Listening strictly on localhost:8080                         │   │
│   │ - CORS whitelisted for https://mirro.in                        │   │
│   │ - Forwarded header awareness enabled                           │   │
│   │ - Stateless JWT authentication (15m access, 7d refresh)        │   │
│   └──────────────────────────────┬─────────────────────────────────┘   │
│                                  │ Local JDBC Loopback (127.0.0.1)     │
│                                  ▼                                     │
│   ┌────────────────────────────────────────────────────────────────┐   │
│   │ PostgreSQL Database 17                                         │   │
│   │ - Port 5432 bound to 127.0.0.1 only                            │   │
│   │ - BLOCKED from public Internet and router port-forwarding      │   │
│   │ - External HDD Automated Backups via Task Scheduler            │   │
│   └────────────────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. PostgreSQL Local Setup & Isolation

- **Location:** Runs on the office PC as the Windows service `postgresql-x64-17`.
- **Database Name:** `ritham_erp`
- **Port:** `5432` (Loopback only).
- **Network Isolation:**
  - In `C:\Program Files\PostgreSQL\17\data\postgresql.conf`:
    ```ini
    listen_addresses = 'localhost'
    port = 5432
    ```
  - In `C:\Program Files\PostgreSQL\17\data\pg_hba.conf`:
    ```ini
    # Allow connections ONLY from IPv4 and IPv6 loopback
    host    ritham_erp    ritham    127.0.0.1/32    scram-sha-256
    host    ritham_erp    ritham    ::1/128         scram-sha-256
    ```
- **Strict Rule:** PostgreSQL port `5432` is **NEVER** forwarded on the office router and **NEVER** added to Cloudflare Tunnel.

---

## 3. Spring Boot Local Setup & Production Profile

- **Executable / Runtime:** Java 17 OpenJDK + Maven wrapper (`.\mvnw.cmd`).
- **Profile:** `prod` (loads `application.yaml` + `application-prod.yaml`).
- **Local Listening Port:** `8080` (loopback).
- **Reverse Proxy Awareness:** `server.forward-headers-strategy: framework` is active in `application-prod.yaml`, allowing Spring Boot to honor `X-Forwarded-Proto: https` and `X-Forwarded-Host: api.mirro.in` sent by Cloudflare.

---

## 4. Environment Variables (`.env`)

Before running in production, set the following environment variables on the host machine (or in a `.env` file that is never committed):

| Variable Name | Example / Recommended Value | Description |
| :--- | :--- | :--- |
| `SPRING_PROFILES_ACTIVE` | `prod` | Activates production configuration |
| `SERVER_PORT` | `8080` | Local loopback port |
| `DB_HOST` | `localhost` | Local database host |
| `DB_PORT` | `5432` | Local PostgreSQL port |
| `DB_NAME` | `ritham_erp` | Database name |
| `DB_USERNAME` | `ritham` | PostgreSQL database user |
| `DB_PASSWORD` | `<secret>` | PostgreSQL password (never hardcoded) |
| `JWT_SECRET` | `<256-bit base64>` | Cryptographic key for signing JWTs |
| `CORS_ORIGIN_1` | `https://mirro.in` | Primary frontend domain |
| `CORS_ORIGIN_2` | `https://www.mirro.in` | Secondary frontend domain |
| `MIGRATION_UPLOAD_DIR` | `D:/ritham-erp-uploads/migration`| Persistent OCR file directory |
| `TESSERACT_DATA_PATH` | `D:/ritham-erp/tessdata` | Tesseract model directory |

To generate a secure 256-bit Base64 JWT secret in PowerShell:
```powershell
[Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Minimum 0 -Maximum 256 }))
```

---

## 5. Frontend API Configuration Strategy

The frontend dynamically detects its runtime host in [constants.js](file:///d:/ritham-erp/frontend/js/constants.js):

- **When hosted on GitHub Pages (`https://mirro.in` / `https://www.mirro.in`):**
  - `API_HOST` resolves to `https://api.mirro.in`
  - `API_BASE_URL` resolves to `https://api.mirro.in/api`
  - `API.HEALTH` resolves to `https://api.mirro.in/actuator/health`
- **When running locally (`localhost` / `127.0.0.1`):**
  - If loaded from Spring Boot (`port 8080`): uses relative `/api`
  - If loaded from standalone dev server (e.g. `5500`, `3000`): uses `http://localhost:8080/api`

---

## 6. CORS Configuration

Cross-Origin Resource Sharing is strictly enforced in [CorsConfig.java](file:///d:/ritham-erp/src/main/java/com/ritham/erp/common/config/CorsConfig.java) and [application-prod.yaml](file:///d:/ritham-erp/src/main/resources/application-prod.yaml):

- **Allowed Origins:** `https://mirro.in`, `https://www.mirro.in`
- **Allowed Methods:** `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`
- **Allowed Headers:** `Authorization`, `Content-Type`, `Accept`, `X-Branch-Id`, `Origin`, `X-Requested-With`, `Cache-Control`
- **Exposed Headers:** `Authorization`, `Content-Disposition`
- **Allow Credentials:** `true`
- **Max Age:** `3600` seconds (1 hour pre-flight cache)

Wildcard `*` origins are completely prohibited in production.

---

## 7. Cloudflare Tunnel Setup (`api.mirro.in`)

Cloudflare Tunnel creates a lightweight, outbound-only tunnel between your local office PC and Cloudflare's edge network:

### Step 1: Install `cloudflared` on Windows
Download and install `cloudflared-windows-amd64.msi` from Cloudflare or run via PowerShell:
```powershell
winget install Cloudflare.cloudflared
```

### Step 2: Authenticate and Create Tunnel
```powershell
cloudflared tunnel login
cloudflared tunnel create ritham-erp-tunnel
```
*(This produces a Tunnel ID and creates a credentials JSON file in `%USERPROFILE%\.cloudflared\<TUNNEL_ID>.json`)*

### Step 3: Create Tunnel Configuration (`config.yml`)
Create `C:\Users\admin\.cloudflared\config.yml`:
```yaml
tunnel: <YOUR_TUNNEL_UUID>
credentials-file: C:\Users\admin\.cloudflared\<YOUR_TUNNEL_UUID>.json

ingress:
  - hostname: api.mirro.in
    service: http://localhost:8080
    originRequest:
      noTLSVerify: false
      httpHostHeader: api.mirro.in
  - service: http_status:404
```

### Step 4: Route DNS & Test
```powershell
cloudflared tunnel route dns ritham-erp-tunnel api.mirro.in
cloudflared tunnel run ritham-erp-tunnel
```

### Step 5: Install as Windows Service
To ensure the tunnel starts automatically when Windows boots:
```powershell
cloudflared service install
Start-Service cloudflared
```

---

## 8. DNS Architecture & Records

Configure the following records in your domain's DNS manager (Cloudflare DNS):

| Type | Name | Content / Target | Proxy Status | Purpose |
| :--- | :--- | :--- | :---: | :--- |
| **A** | `@` (`mirro.in`) | `185.199.108.153` | DNS only | GitHub Pages IP 1 |
| **A** | `@` (`mirro.in`) | `185.199.109.153` | DNS only | GitHub Pages IP 2 |
| **A** | `@` (`mirro.in`) | `185.199.110.153` | DNS only | GitHub Pages IP 3 |
| **A** | `@` (`mirro.in`) | `185.199.111.153` | DNS only | GitHub Pages IP 4 |
| **CNAME**| `www` | `mirrodevelopment.github.io` | DNS only | GitHub Pages www alias |
| **CNAME**| `api` | `<TUNNEL_UUID>.cfargotunnel.com` | **Proxied** (Orange Cloud) | Cloudflare Tunnel to local Spring Boot |

*(Note: In GitHub repository **Settings &rarr; Pages**:*
*1. Under **Build and deployment &rarr; Source**, select **GitHub Actions** (which uses `.github/workflows/deploy-pages.yml` to automatically deploy the `frontend/` directory to root).*
*2. Under **Custom domain**, enter `mirro.in`, ensure `frontend/CNAME` is recognized, and check **"Enforce HTTPS"** once DNS records propagate).*

---

## 9. HTTPS Configuration

- **Frontend (`https://mirro.in`):** Managed automatically with Let's Encrypt certificates by GitHub Pages.
- **Backend (`https://api.mirro.in`):** Managed automatically with Cloudflare Edge SSL.
  - Set Cloudflare SSL/TLS Encryption Mode to **Full (Strict)** or **Full**.
  - Communication between Cloudflare and the office computer travels over the Cloudflare Tunnel's end-to-end encrypted QUIC/TLS channel.

---

## 10. Windows Firewall Hardening

To ensure absolute database privacy, execute the following in an Administrator PowerShell window on the office PC:

```powershell
# 1. Block external inbound access to PostgreSQL port 5432
New-NetFirewallRule -DisplayName "Block Remote Inbound PostgreSQL 5432" `
  -Direction Inbound -LocalPort 5432 -Protocol TCP -Action Block `
  -RemoteAddress "10.0.0.0/8","172.16.0.0/12","192.168.0.0/16","Internet"

# 2. Verify port 8080 does not accept direct Internet connections (Tunnel handles access)
# (Only local loopback 127.0.0.1 is required)
```

---

## 11. Backup & Disaster Recovery

The system includes automated PowerShell backup and restore automation in `scripts/backup/`:

- **Scheduled Engine:** [setup-task-scheduler.ps1](file:///d:/ritham-erp/scripts/backup/setup-task-scheduler.ps1) sets up a daily Windows Task Scheduler job at 11:00 PM.
- **Execution Script:** [erp-backup.ps1](file:///d:/ritham-erp/scripts/backup/erp-backup.ps1) executes `pg_dump` into `E:\ERP-Backups\Database\Daily\` using custom compressed archive format (`.dump`) and manages 7-day, 4-week, and 12-month retention.
- **Verification Script:** [erp-restore.ps1](file:///d:/ritham-erp/scripts/backup/erp-restore.ps1) enables safe verification drill into sandbox database `ritham_erp_restore_test`.
- **Handbook:** Detailed recovery instructions are documented in [DISASTER_RECOVERY.md](file:///d:/ritham-erp/scripts/backup/DISASTER_RECOVERY.md).

---

## 12. Startup & Restart Procedures

### Manual Startup via Production Command
```powershell
# Set production environment variables
$env:SPRING_PROFILES_ACTIVE = "prod"
$env:DB_USERNAME            = "ritham"
$env:DB_PASSWORD            = "YourSecurePassword"
$env:JWT_SECRET             = "YourBase64SecretKey"

# Launch Spring Boot
.\mvnw.cmd spring-boot:run
```

### Automated Windows Service Setup (Recommended for 24/7 Office PC)
Using WinSW (Windows Service Wrapper) or Task Scheduler "At System Startup", configure Spring Boot as an automatic background service so that:
1. Windows boots &rarr; PostgreSQL service starts automatically.
2. Spring Boot service starts automatically on `localhost:8080`.
3. Cloudflare Tunnel service connects automatically to Cloudflare's edge.

---

## 13. What Happens If the Office PC Is Offline?

- **Frontend Availability:** The web interface at `https://mirro.in` remains 100% accessible (hosted globally by GitHub Pages).
- **Graceful Offline Indication:** The header connection monitor dot will turn red (**OFFLINE**).
- **API Responses:** Cloudflare will return HTTP `530 Origin DNS Error` or HTTP `502 Bad Gateway` indicating the local tunnel daemon is unreachable.
- **Data Protection:** No data can be corrupted or accessed while the machine is offline.
- **Recovery:** As soon as the office PC is powered back on and connected to the Internet, the `cloudflared` service reconnects automatically within 10–15 seconds, and the frontend connection monitor turns green (**LIVE**) with zero manual intervention.
