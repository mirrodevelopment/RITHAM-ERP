# Ritham ERP — Quick Start & Setup Guide

## 🚀 One-Click Launch (Windows)

Simply double-click **`start.bat`** in the project root directory!

`start.bat` will automatically:
1. Boot the Spring Boot backend server (`.\mvnw.cmd spring-boot:run`) on `http://localhost:8080`.
2. Poll `http://localhost:8080/actuator/health` and automatically open `http://localhost:8080/desks/login/login.html` in your default web browser.

---

## 🛠️ Manual Start Instructions

### Step 1: Start Backend Server
```powershell
cd d:\ritham-erp
.\mvnw.cmd spring-boot:run
```

### Step 2: Open Frontend in Web Browser
Open **`http://localhost:8080/desks/login/login.html`** in Chrome, Edge, or Firefox (served directly by Spring Boot static resources; avoid `file:///` URLs).

---

## 🔑 Default Login Credentials

| Account | Username | Password | Access Level |
|---------|----------|----------|--------------|
| **System Administrator** | `admin` | `Admin@123` | Full Enterprise Access (Global) |

---

## 🛑 Stopping the Application
- Press `Ctrl+C` in the terminal window to stop the Spring Boot server.
