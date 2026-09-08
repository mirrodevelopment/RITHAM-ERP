@echo off
title Ritham ERP — Enterprise System Launcher
cls

echo =======================================================================
echo                       RITHAM ERP (Version 1.0)                         
echo       Tailoring, Boutique and Garment Manufacturing Enterprise ERP     
echo =======================================================================
echo.

echo [1/2] Launching Backend Health Monitor...
echo Application will automatically open in browser once backend server is ready.
echo Login Desk URL: http://localhost:8080/desks/login/login.html
echo.

start /b powershell -NoProfile -ExecutionPolicy Bypass -Command "for ($i=0; $i -lt 60; $i++) { try { $r = Invoke-WebRequest -Uri 'http://localhost:8080/actuator/health' -UseBasicParsing -TimeoutSec 2; if ($r.StatusCode -eq 200) { Start-Process 'http://localhost:8080/desks/login/login.html'; break } } catch {}; Start-Sleep -Seconds 1 }"

echo [2/2] Starting Spring Boot Server on http://localhost:8080...
echo Website Endpoint: http://localhost:8080/
echo Login Desk      : http://localhost:8080/desks/login/login.html
echo REST API Base   : http://localhost:8080/api
echo Default Login   : admin / Admin@123
echo.
echo =======================================================================
echo   Server logs will stream below. Press Ctrl+C to stop the application.
echo =======================================================================
echo.

call "%~dp0mvnw.cmd" spring-boot:run

pause
