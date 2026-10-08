/**
 * Ritham ERP — Application Constants
 * All configuration values and route definitions live here.
 * Loaded first on every page.
 */

'use strict';

// ── API Configuration ────────────────────────────────────────────────────────

// Use a relative path so the app works in any deployment (localhost, staging, production).
// The Spring Boot server serves both the API and the static frontend from the same origin.
const API_BASE_URL = '/api';

const API = {
  // Auth
  LOGIN:   `${API_BASE_URL}/auth/login`,
  LOGOUT:  `${API_BASE_URL}/auth/logout`,
  REFRESH: `${API_BASE_URL}/auth/refresh`,
  ME:      `${API_BASE_URL}/auth/me`,

  // Branches
  BRANCHES:        `${API_BASE_URL}/branches`,
  BRANCH:          (id) => `${API_BASE_URL}/branches/${id}`,

  // Employees
  EMPLOYEES:      `${API_BASE_URL}/employees`,
  EMPLOYEE:       (id) => `${API_BASE_URL}/employees/${id}`,
  EMPLOYEE_STATUS:(id) => `${API_BASE_URL}/employees/${id}/status`,

  // Customers
  CUSTOMERS:  `${API_BASE_URL}/customers`,
  CUSTOMER:   (mobile) => `${API_BASE_URL}/customers/${mobile}`,

  // Orders
  ORDERS:          `${API_BASE_URL}/orders`,
  ORDER:           (id) => `${API_BASE_URL}/orders/${id}`,
  ORDER_CANCEL:    (id) => `${API_BASE_URL}/orders/${id}/cancel`,
  ORDERS_STATS:    `${API_BASE_URL}/orders/stats`,
  ORDERS_ANALYTICS:`${API_BASE_URL}/orders/analytics`,
  ORDER_PAYMENTS:  (id) => `${API_BASE_URL}/orders/${id}/payments`,

  // Design (Phase 8 Planned — placeholder desk in /desks/design/)
  DESIGNS:       `${API_BASE_URL}/designs`,
  DESIGN:        (id) => `${API_BASE_URL}/designs/${id}`,
  DESIGN_IMAGES: (id) => `${API_BASE_URL}/designs/${id}/images`,

  // Measurements
  MEASUREMENTS:          `${API_BASE_URL}/customer-measurements`,
  MEASUREMENT:           (id) => `${API_BASE_URL}/customer-measurements/${id}`,
  CUSTOMER_MEASUREMENTS: `${API_BASE_URL}/customer-measurements`,
  CUSTOMER_MEASUREMENT:  (mobile) => `${API_BASE_URL}/customer-measurements/customer/${mobile}`,

  // Production Stages
  PRODUCTION_STAGES:     `${API_BASE_URL}/production-stages`,

  // Materials (Phase 10 Planned — placeholder desk in /desks/material/)
  MATERIALS: `${API_BASE_URL}/materials`,

  // Production (Workstations workflow operates via /api/orders & /api/production-stages)
  PRODUCTION:       `${API_BASE_URL}/production`,
  PRODUCTION_ORDER: (id) => `${API_BASE_URL}/production/${id}`,
  PRODUCTION_STAGE: (id) => `${API_BASE_URL}/production/${id}/stage`,

  // Payments (Active order-level ledger uses ORDER_PAYMENTS; standalone /api/payments reserved for Phase 12)
  PAYMENTS: `${API_BASE_URL}/payments`,
  PAYMENT:  (id) => `${API_BASE_URL}/payments/${id}`,

  // Deliveries (Active delivery desk operates via /api/orders/{id}/status; /api/deliveries reserved as alias)
  DELIVERIES: `${API_BASE_URL}/deliveries`,
  DELIVERY:   (id) => `${API_BASE_URL}/deliveries/${id}`,

  // Dashboard (Reserved for Phase 14-16 summary endpoints)
  DASHBOARD_STATS: `${API_BASE_URL}/dashboard/stats`,
  DASHBOARD_LIVE:  `${API_BASE_URL}/dashboard/live`,

  // Reports (Active reporting operates via ORDERS_ANALYTICS: /api/orders/analytics)
  REPORTS: `${API_BASE_URL}/reports`,

  // Historical Data Migration
  MIGRATION_BATCHES:          `${API_BASE_URL}/migration/batches`,
  MIGRATION_BATCH:            (id) => `${API_BASE_URL}/migration/batches/${id}`,
  MIGRATION_STATS:            `${API_BASE_URL}/migration/statistics`,
  MIGRATION_BATCH_DOCUMENTS:  (id) => `${API_BASE_URL}/migration/batches/${id}/documents`,
  MIGRATION_DOCUMENT:         (id) => `${API_BASE_URL}/migration/documents/${id}`,
  MIGRATION_DOCUMENT_FILE:    (id) => `${API_BASE_URL}/migration/documents/${id}/file`,
  MIGRATION_DOCUMENT_ORIGINAL_IMAGE: (id) => `${API_BASE_URL}/migration/documents/${id}/original-image`,
  MIGRATION_DOCUMENT_PROCESSED_IMAGE: (id) => `${API_BASE_URL}/migration/documents/${id}/processed-image`,
  MIGRATION_DOCUMENT_OCR:     (id) => `${API_BASE_URL}/migration/documents/${id}/ocr`,
  MIGRATION_DOCUMENT_EXTRACT: (id) => `${API_BASE_URL}/migration/documents/${id}/extract`,
  MIGRATION_DOCUMENT_RESCAN:  (id) => `${API_BASE_URL}/migration/documents/${id}/rescan`,
  MIGRATION_DOCUMENT_REVIEW:  (id) => `${API_BASE_URL}/migration/documents/${id}/review`,
  MIGRATION_DOCUMENT_REVIEWS: (id) => `${API_BASE_URL}/migration/documents/${id}/reviews`,
  MIGRATION_DOCUMENT_MATCHES: (id) => `${API_BASE_URL}/migration/documents/${id}/matches`,
  MIGRATION_DOCUMENT_IMPORT:  (id) => `${API_BASE_URL}/migration/documents/${id}/import`,
  MIGRATION_DOCUMENT_PAGES:   (id) => `${API_BASE_URL}/migration/documents/${id}/pages`,
  MIGRATION_DOCUMENT_SOURCE_FILE: (id) => `${API_BASE_URL}/migration/documents/${id}/source-file`,
  MIGRATION_DOCUMENT_APPROVE_ALL_PAGES: (id) => `${API_BASE_URL}/migration/documents/${id}/approve-all-pages`,
  MIGRATION_DOCUMENT_IMPORT_ALL_PAGES: (id) => `${API_BASE_URL}/migration/documents/${id}/import-all-pages`,
  MIGRATION_CLEAR_ALL:        `${API_BASE_URL}/migration/clear-all`,

  // Backup Management (System Administrator)
  BACKUP_STATUS:              `${API_BASE_URL}/admin/backup/status`,
  BACKUP_LOCATIONS:           `${API_BASE_URL}/admin/backup/locations`,
  BACKUP_SET_LOCATION:        `${API_BASE_URL}/admin/backup/location`,
  BACKUP_FILES:               `${API_BASE_URL}/admin/backup/files`,
  BACKUP_LOGS:                `${API_BASE_URL}/admin/backup/logs`,
  BACKUP_RUN:                 `${API_BASE_URL}/admin/backup/run`,
  BACKUP_DOWNLOAD:            (cat, file, loc) => `${API_BASE_URL}/admin/backup/download/${cat}/${encodeURIComponent(file)}${loc ? `?location=${encodeURIComponent(loc)}` : ''}`,
  BACKUP_VERIFY:              (cat, file, loc) => `${API_BASE_URL}/admin/backup/verify/${cat}/${encodeURIComponent(file)}${loc ? `?location=${encodeURIComponent(loc)}` : ''}`,
  BACKUP_UPLOAD:              `${API_BASE_URL}/admin/backup/upload`,
  BACKUP_INSPECT:             `${API_BASE_URL}/admin/backup/inspect`,
  BACKUP_RESTORE:             `${API_BASE_URL}/admin/backup/restore`,
};

// ── Page Routes ──────────────────────────────────────────────────────────────

const ROUTES = {
  LOGIN:            '/desks/login/login.html',
  ORDER_DESK:       '/desks/order-desk/order-desk.html',
  PRODUCTION_DESK:  '/desks/production-desk/production-desk.html',
  OPERATIONS_DESK:  '/desks/operations-desk/operations-desk.html',
  CUSTOMER:         '/desks/customer/customer.html',
  ORDER:            '/desks/order/order.html',
  EMPLOYEE:         '/desks/employee/employee.html',
  USER:             '/desks/user/user.html',
  STAGE:            '/desks/stage/stage.html',
  MEASUREMENT:      '/desks/measurement/measurement.html',
  PRODUCTION:       '/desks/production/production.html',
  PAYMENT:          '/desks/payment/payment.html',
  DELIVERY:         '/desks/delivery/delivery.html',
  REPORT:           '/desks/report/report.html',
  BRANCH:           '/desks/branch/branch.html',
  MIGRATION:        '/desks/migration/migration.html',
  BACKUP:           '/desks/backup/backup.html',
};

// ── Roles ────────────────────────────────────────────────────────────────────

const ROLES = {
  ADMIN:               'ROLE_ADMIN',
  OPERATIONS_MANAGER:  'ROLE_OPERATIONS_MANAGER',
  PRODUCTION_EMPLOYEE: 'ROLE_PRODUCTION_EMPLOYEE',
  RECEPTION:           'ROLE_RECEPTION',
};

// Role display names
const ROLE_LABELS = {
  [ROLES.ADMIN]:               'Administrator',
  [ROLES.OPERATIONS_MANAGER]:  'Operations Manager',
  [ROLES.PRODUCTION_EMPLOYEE]: 'Production',
  [ROLES.RECEPTION]:           'Reception',
};

// Default dashboard by role after login
const ROLE_DEFAULT_DASHBOARD = {
  [ROLES.ADMIN]:               ROUTES.ORDER_DESK,
  [ROLES.OPERATIONS_MANAGER]:  ROUTES.OPERATIONS_DESK,
  [ROLES.PRODUCTION_EMPLOYEE]: ROUTES.PRODUCTION_DESK,
  [ROLES.RECEPTION]:           ROUTES.ORDER_DESK,
};

// ── App Config ───────────────────────────────────────────────────────────────

const APP = {
  NAME:        'Ritham ERP',
  VERSION:     '1.0.0',
  COMPANY:     'Ritham Garments',
  TOKEN_KEY:     'ritham_access_token',
  REFRESH_KEY:   'ritham_refresh_token',
  USER_KEY:      'ritham_user',
  BRANCH_HEADER: 'X-Branch-Id',
};

// ── HTTP Status ──────────────────────────────────────────────────────────────

const HTTP = {
  OK:           200,
  CREATED:      201,
  NO_CONTENT:   204,
  BAD_REQUEST:  400,
  UNAUTHORIZED: 401,
  FORBIDDEN:    403,
  NOT_FOUND:    404,
  CONFLICT:     409,
  SERVER_ERROR: 500,
};
