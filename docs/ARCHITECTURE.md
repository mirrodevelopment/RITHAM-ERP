# Ritham ERP — System Architecture & Design Specification

## Overview
**Ritham ERP** is a production-centric, multi-branch ERP designed specifically for tailoring, boutique, and garment manufacturing businesses. The system manages the complete lifecycle of a customer order—from order booking and measurement profiling to final delivery—while tracking production stages, responsible employees, payment ledgers, historical slip digitization (OCR), and backup/disaster recovery.

Unlike traditional ERPs, **the Order is the core business entity**, and every workflow revolves around the **Order ID**.

The system enforces a **clean separation between Frontend and Backend**:
- **Backend:** Spring Boot 4.1 (Java 17), PostgreSQL 17, Flyway, Spring Security (JWT + BranchContext), JPA/Hibernate, Tess4J + PDFBox (OCR migration).
- **Frontend:** Pure HTML5, Vanilla CSS3, Vanilla JavaScript (ES6+). Zero external JS frameworks. Served locally from Spring Boot static resources (`http://localhost:8080`) or deployed via GitHub Pages (`.github/workflows/deploy-pages.yml`) talking to a Cloudflare Tunnel backend (`application-prod.yaml`).

---

## Core Design Principles

### 1. Order-Centric & Multi-Branch Architecture
- **Branch Isolation (`BranchContext`)**: `employees`, `customer_orders`, and `migration_batches` are scoped to `branch_id`. Global Admins (`ROLE_ADMIN` with `branch_id == null`) can inspect across branches via `X-Branch-Id`, while branch-assigned staff are strictly isolated to their branch.
- **Customer** ➔ Creates **Orders** (tracked per branch).
- Every **production status**, **measurement snapshot (`order_measurements`)**, and **payment ledger entry (`order_payments`)** belongs to an `Order`.

### 2. Minimal Customer Database
- `customer_mobile` (`VARCHAR(15)`, Primary Key, Business Identifier & Search Key).
- `customer_name` (`VARCHAR(100)`, Required).
- **Saved Measurement Profiles (`customer_measurements`)**: Linked by `(customer_mobile, garment_type)` with JSON measurement maps for `BLOUSE` (19 fields) and `CHUDI` (18 fields).

### 3. Mobile-First Production & Role-Separated Operations
- Production floor staff use mobile/tablet views to advance garment stages and assign workers.
- Operations Managers monitor production flow across all stages with financial amounts hidden across UI tables and exports.

### 4. Historical Migration & Automated Backup
- **Historical Migration (`migration_*`)**: Multi-page PDF/camera intake, orientation detection & Tess4J OCR extraction, field completion checks, human review workflow, and immutable import into `customers` / `customer_orders` / `order_measurements` / `customer_measurements`.
- **Backup & DR (`scripts/backup/` + `/api/backups`)**: Scheduled PowerShell `pg_dump` + upload snapshots with SHA-256 manifests and safe `TEST` / `PRODUCTION` restore workflows.

---

## Production Workflow (12 Standard Stages Seeded in `V2`)

Custom active stages can also be added and reordered dynamically via `/api/production-stages`:

```text
PENDING (Order Booked)
     │
     ▼
1.  DESIGNING           (Designing)
     │
     ▼
2.  LINING              (Lining)
     │
     ▼
3.  HAND_MACHINE_WORK   (Hand Work / Machine Work)
     │
     ▼
4.  INITIAL_IRONING     (Initial Ironing)
     │
     ▼
5.  CUTTING             (Cutting)
     │
     ▼
6.  STRETCHING          (Stretching)
     │
     ▼
7.  STITCHING           (Stitching)
     │
     ▼
8.  HEMMING             (Hemming)
     │
     ▼
9.  FINAL_IRONING       (Final Ironing)
     │
     ▼
10. QUALITY_CHECK       (Quality Check (QC))
     │
     ▼
11. READY_TO_DELIVERY   (Ready to Delivery)
     │
     ▼
12. DELIVERY / DELIVERED (Payment Collection, Receiver Tracking & Handoff)
```

---

## High-Level Architecture Diagram

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│                            FRONTEND LAYER                                   │
│  ┌──────────────┐ ┌──────────────────┐ ┌────────────────┐ ┌──────────────┐  │
│  │  Order Desk  │ │ Production Floor │ │ Operations Desk│ │ Admin/Reports│  │
│  │  (Reception) │ │  (Mobile/Tablet) │ │ (All Devices)  │ │ Migration/DR │  │
│  └──────┬───────┘ └────────┬─────────┘ └───────┬────────┘ └──────┬───────┘  │
│         │                  │                   │                 │          │
│  ┌──────┴──────────────────┴───────────────────┴─────────────────┴───────┐  │
│  │   UI Components & Utilities (sidebar, header, api.js, auth.js, utils) │  │
│  └───────────────────────────────────┬───────────────────────────────────┘  │
└──────────────────────────────────────┼──────────────────────────────────────┘
                                       │ HTTPS / REST API (Authorization: Bearer JWT)
┌──────────────────────────────────────┴──────────────────────────────────────┐
│                            BACKEND LAYER                                    │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │  REST Controllers (/api/auth, /api/orders, /api/employees,            │  │
│  │  /api/customers, /api/measurements, /api/production-stages,           │  │
│  │  /api/branches, /api/migration, /api/backups)                         │  │
│  └───────────────────────────────────┬───────────────────────────────────┘  │
│  ┌───────────────────────────────────┴───────────────────────────────────┐  │
│  │  Security Filter Chain (JwtAuthFilter, BranchContext, ClientIpResolver)│ │
│  └───────────────────────────────────┬───────────────────────────────────┘  │
│  ┌───────────────────────────────────┴───────────────────────────────────┐  │
│  │  Service Layer (Auth, Order, Customer, Employee, Production,          │  │
│  │  Migration/OCR Extraction, Backup/Restore)                            │  │
│  └───────────────────────────────────┬───────────────────────────────────┘  │
│  ┌───────────────────────────────────┴───────────────────────────────────┐  │
│  │  Data Access Layer (JPA Repositories & Entities)                      │  │
│  └───────────────────────────────────┬───────────────────────────────────┘  │
└──────────────────────────────────────┼──────────────────────────────────────┘
                                       │ SQL / JDBC
┌──────────────────────────────────────┴──────────────────────────────────────┐
│                            DATABASE LAYER                                   │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │  PostgreSQL 17 Database (Flyway Consolidated Migrations)              │  │
│  │  - V1__init_schema.sql: branches, roles, departments, employees,      │  │
│  │    refresh_tokens, customers, customer_measurements, customer_orders, │  │
│  │    order_designs, order_measurements, order_materials,                │  │
│  │    production_tracking, order_payments, order_deliveries,             │  │
│  │    production_stages, migration_batches, migration_documents,         │  │
│  │    migration_reviews, migration_imports                               │  │
│  │  - V2__seed_initial_data.sql: roles, departments, 12 production stages│  │
│  │    & default system employees                                         │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Security & Authentication Model

1. **Authentication:**
   - Stateless JWT authentication via HTTP headers (`Authorization: Bearer <token>` only — query-parameter tokens rejected).
   - Token pair strategy: Access Token (`900000 ms` / 15 min) + Refresh Token (`604800000 ms` / 7 days).
   - Atomic refresh token rotation enforcing `isActive` user validation and `LoginRateLimiter` protection across unknown and known identifiers.
   - Reverse-proxy support via `server.forward-headers-strategy: framework` (`prod`) and trusted-loopback `ClientIpResolver`.

2. **Authorization (RBAC):**
   - `ROLE_ADMIN` — Full enterprise access, branch management, backups, migration & archive cleanup (`branch_id == null` for Global Admin).
   - `ROLE_OPERATIONS_MANAGER` — Operations monitoring, stage management, reports (financial amounts hidden in UI/exports).
   - `ROLE_PRODUCTION_EMPLOYEE` — Production task execution & stage updates.
   - `ROLE_RECEPTION` — Customer registration, order taking desk, measurement intake & delivery receipt handling.
