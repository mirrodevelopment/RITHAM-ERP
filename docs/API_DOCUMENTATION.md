# Ritham ERP — REST API Documentation (Version 1.0)

## Base URL
`http://localhost:8080/api`

---

## 1. Authentication Endpoints (`/api/auth`)

### Login
- **Endpoint:** `POST /api/auth/login`
- **Auth Required:** No
- **Request Body:**
  ```json
  {
    "username": "admin",
    "password": "Admin@123"
  }
  ```
- **Response:**
  ```json
  {
    "success": true,
    "message": "Login successful",
    "data": {
      "employeeId": 1,
      "employeeCode": "EMP-001",
      "fullName": "System Administrator",
      "username": "admin",
      "role": "ROLE_ADMIN",
      "accessToken": "eyJ...",
      "refreshToken": "eyJ...",
      "accessTokenExpiresInMs": 900000
    }
  }
  ```

### Refresh Token
- **Endpoint:** `POST /api/auth/refresh`
- **Auth Required:** No

### Logout
- **Endpoint:** `POST /api/auth/logout`
- **Auth Required:** Yes (`Bearer <token>`)

---

## 2. Customer Endpoints (`/api/customers`) — Minimal Customer Spec

### List / Search Customers (Paginated)
- **Endpoint:** `GET /api/customers?search=9876543210&page=0&size=20`
- **Auth Required:** Yes
- **Response:**
  ```json
  {
    "success": true,
    "data": {
      "content": [
        {
          "customerMobile": "9876543210",
          "customerName": "Anitha Ramesh",
          "createdAt": "2026-08-06T11:30:00"
        }
      ],
      "totalElements": 1,
      "totalPages": 1
    }
  }
  ```

### Get Customer by Mobile Number
- **Endpoint:** `GET /api/customers/{mobile}`
- **Auth Required:** Yes
- **Response:**
  ```json
  {
    "success": true,
    "data": {
      "customerMobile": "9876543210",
      "customerName": "Anitha Ramesh",
      "createdAt": "2026-08-06T11:30:00"
    }
  }
  ```

### Create Customer
- **Endpoint:** `POST /api/customers`
- **Auth Required:** Yes
- **Request Body:**
  ```json
  {
    "customerMobile": "9876543210",
    "customerName": "Anitha Ramesh"
  }
  ```

---

## 3. Employee Endpoints (`/api/employees`)

### List Employees
- **Endpoint:** `GET /api/employees?search=ravi&page=0&size=15`
- **Auth Required:** Yes

### Create Employee
- **Endpoint:** `POST /api/employees`
- **Auth Required:** Yes (Role: `ROLE_ADMIN`)

### Toggle Active Status
- **Endpoint:** `PATCH /api/employees/{id}/status`
- **Auth Required:** Yes (Role: `ROLE_ADMIN`)

---

## 4. Customer Order & Payment Endpoints (`/api/orders`)

### List / Search Orders
- **Endpoint:** `GET /api/orders?page=0&size=20&search={query}`
- **Auth Required:** Yes

### Analytics & Financial Aggregation
- **Endpoint:** `GET /api/orders/analytics`
- **Auth Required:** Yes
- **Description:** Server-side financial aggregation across all historical orders (total revenue, total pending, orders by status, payment breakdown, top garments).

### Order Payment Ledger
- **Endpoint:** `GET /api/orders/{id}/payments`
- **Auth Required:** Yes
- **Description:** Retrieves all payment ledger transactions recorded for an order.

### Record Order Payment
- **Endpoint:** `POST /api/orders/{id}/payments`
- **Auth Required:** Yes
- **Request Body:**
  ```json
  {
    "amount": 1500.00,
    "paymentMode": "UPI",
    "paymentDate": "2026-09-05",
    "notes": "Advance payment"
  }
  ```

### Update Order Status / Delivery
- **Endpoint:** `PUT /api/orders/{id}/status`
- **Auth Required:** Yes
- **Request Body:**
  ```json
  {
    "status": "DELIVERED",
    "receiverName": "Anitha Ramesh",
    "finalPayment": 500.00,
    "paymentMode": "CASH"
  }
  ```

---

## 5. Phantom API Audit & Roadmap Matrix

Audit of routes referenced in `constants.js` and architectural status:

| Route | Backend Implementation | Frontend Desk Usage | Roadmap Phase | Classification & Architectural Reality |
| :--- | :--- | :--- | :--- | :--- |
| **`/api/designs`** | ❌ Not implemented | Placeholder shell at `/desks/design/` | Phase 8 | **PLANNED**: Reserved for Phase 8 Design Module deliverable. |
| **`/api/materials`** | ❌ Not implemented | Placeholder shell at `/desks/material/` | Phase 10 | **PLANNED**: Reserved for Phase 10 Material Procurement deliverable. |
| **`/api/payments`** | ⚠️ Standalone: Not implemented.<br>✅ Order-level: **IMPLEMENTED** under `/api/orders/{id}/payments` | Placeholder shell at `/desks/payment/`.<br>Active payments run in `order.js`, `delivery.js`, `billing.js`. | Phase 12 | **PLANNED STANDALONE / IMPLEMENTED VIA ORDER SUB-RESOURCE**: Order ledger operates under `/api/orders/{id}/payments`; standalone `/api/payments` reserved for Phase 12 cross-order payment search. |
| **`/api/deliveries`** | ❌ Standalone: Not implemented.<br>✅ Order-level: **IMPLEMENTED** under `/api/orders/{id}/status` | Fully functional Delivery Desk at `/desks/delivery/` | Phase 13 | **UNUSED ALIAS / IMPLEMENTED VIA ORDER STATUS**: Delivery Desk is fully active, executing delivery workflows via `/api/orders/{id}/status`. |

