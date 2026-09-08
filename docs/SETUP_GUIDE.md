# Ritham ERP — Quick Start & Setup Guide

## 🚀 One-Click Launch (Windows)

Simply double-click **`start.bat`** in the project root directory!

`start.bat` will automatically:
1. Open the frontend UI in your default web browser (`frontend/index.html`).
2. Boot the Spring Boot backend server (`.\mvnw.cmd spring-boot:run`).

---

## 🛠️ Manual Start Instructions

### Step 1: Start Backend Server
```powershell
cd d:\ritham-erp
.\mvnw.cmd spring-boot:run
```

### Step 2: Open Frontend in Web Browser
Open [file:///d:/ritham-erp/frontend/index.html](file:///d:/ritham-erp/frontend/index.html) in Chrome, Edge, or Firefox.

---

## 🔑 Default Login Credentials

| Account | Username | Password | Access Level |
|---------|----------|----------|--------------|
| **System Administrator** | `admin` | `Admin@123` | Full Enterprise Access |

---

## 🛑 Stopping the Application
- Press `Ctrl+C` in the terminal window to stop the Spring Boot server.
