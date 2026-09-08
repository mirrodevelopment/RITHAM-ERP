# Ritham ERP — System Architecture & Design Specification (Version 1.0)

## Overview
**Ritham ERP** is a production-centric ERP designed specifically for tailoring, boutique, and garment manufacturing businesses. The system manages the complete lifecycle of a customer order—from order booking to final delivery—while tracking every production stage, responsible employee, and material usage.

Unlike traditional ERPs, **the Order is the core business entity**, and every workflow revolves around the **Order ID**.

The system enforces a **strict separation between Frontend and Backend**:
- **Backend:** Spring Boot 4.1 (Java 17), PostgreSQL, Flyway, Spring Security (JWT), JPA/Hibernate.
- **Frontend:** Pure HTML5, Vanilla CSS3, Vanilla JavaScript (ES6+). Zero external JS frameworks.

---

## Core Design Principles (from Project Specification PDF)

### 1. Order-Centric Architecture
The entire ERP revolves around **Order ID**:
- **Customer** ➔ Creates Orders.
- Every **production activity** belongs to an Order.
- **Payments** belong to an Order.
- **Delivery** belongs to an Order.
- **Production Philosophy:** Production is **never linked directly to Customer**. Everything inside production references ONLY `Order ID`. This enables independent production, payment, and delivery tracking for multiple orders from the same customer.

### 2. Minimal Customer Database
Customer information is intentionally simple:
- `customer_mobile` (VARCHAR 15, **Primary Key**, Business Identifier, Search Key).
- `customer_name` (VARCHAR 100, Required).
- *No unnecessary information like address, email, gender, DOB, etc.*
- **Instant Search:** Searching by mobile number immediately displays: Customer Name, Previous Orders, Saved Measurements, Pending Payments, Completed Orders, and Delivery History.

### 3. Mobile-First Production
Production floor employees use mobile phones or tablets to update stage progress, start/end times, remarks, and images without requiring a desktop.

### 4. Centralized Operations Monitoring
Management monitors live production status, stage progress, employee productivity, and bottlenecks from a unified operations dashboard.

---

## Production Workflow (11 Stages)

```text
Order Created
     │
     ▼
Design Details
     │
     ▼
Measurements
     │
     ▼
Material Procurement  ───►  Company Material / Customer Material
     │
     ▼
Cutting
     │
     ▼
Stitching             ───►  In-House Stitching / Outsourced Stitching
     │
     ▼
Hemming
     │
     ▼
Quality Check (QC)
     │
     ▼
Ironing
     │
     ▼
Finishing
     │
     ▼
Packing
     │
     ▼
Payment Collection    ───►  Cash / Google Pay / UPI / Bank Transfer
     │
     ▼
Delivery & Receipt Printing
```

---

## High-Level Architecture Diagram

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│                            FRONTEND LAYER                                   │
│  ┌──────────────────────┐ ┌──────────────────────┐ ┌─────────────────────┐  │
│  │     Order Desk       │ │   Production Floor   │ │   Operations Desk   │  │
│  │    (Dashboard 1)     │ │    (Dashboard 2)     │ │    (Dashboard 3)    │  │
│  │    Desktop Only      │ │   Mobile & Tablet    │ │   All Devices       │  │
│  └──────────┬───────────┘ └──────────┬───────────┘ └──────────┬──────────┘  │
│             │                        │                        │             │
│  ┌──────────┴────────────────────────┴────────────────────────┴──────────┐  │
│  │   UI Components & Utilities (sidebar, header, api.js, auth.js, theme)  │  │
│  └───────────────────────────────────┬───────────────────────────────────┘  │
└──────────────────────────────────────┼──────────────────────────────────────┘
                                       │ HTTP / REST API (JWT Authorization)
┌──────────────────────────────────────┴──────────────────────────────────────┐
│                            BACKEND LAYER                                    │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │  REST Controllers (/api/auth, /api/employees, /api/customers, etc.)   │  │
│  └───────────────────────────────────┬───────────────────────────────────┘  │
│  ┌───────────────────────────────────┴───────────────────────────────────┐  │
│  │  Security Filter Chain (JwtAuthFilter, DaoAuthenticationProvider)      │  │
│  └───────────────────────────────────┬───────────────────────────────────┘  │
│  ┌───────────────────────────────────┴───────────────────────────────────┐  │
│  │  Service Layer (AuthService, EmployeeService, CustomerService, etc.)  │  │
│  └───────────────────────────────────┬───────────────────────────────────┘  │
│  ┌───────────────────────────────────┴───────────────────────────────────┐  │
│  │  Data Access Layer (JPA Repositories & Entities)                      │  │
│  └───────────────────────────────────┬───────────────────────────────────┘  │
└──────────────────────────────────────┼──────────────────────────────────────┘
                                       │ SQL / JDBC
┌──────────────────────────────────────┴──────────────────────────────────────┐
│                            DATABASE LAYER                                   │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │  PostgreSQL Database                                                  │  │
│  │  - V1: roles, departments, employees, refresh_tokens                  │  │
│  │  - V2: customers (mobile PK), customer_orders, order_designs,         │  │
│  │        order_measurements, order_materials, production_tracking,      │  │
│  │        order_payments, order_deliveries                               │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Security & Authentication Model

1. **Authentication:**
   - Stateless JWT authentication via HTTP headers (`Authorization: Bearer <token>`).
   - Token pair strategy: Access Token (15 min) + Refresh Token (7 days).
   - Atomic refresh token rotation with stored active refresh tokens.

2. **Authorization (RBAC):**
   - `ROLE_ADMIN` — Full enterprise access & system settings.
   - `ROLE_OPERATIONS_MANAGER` — Operations, production monitoring, orders, reports.
   - `ROLE_PRODUCTION_EMPLOYEE` — Assigned production tasks & stage updates.
   - `ROLE_RECEPTION` — Customer registration, order taking desk, receipt printing.
