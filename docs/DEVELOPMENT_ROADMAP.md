# Ritham ERP — Enterprise Development Roadmap

| Phase | Phase Name | Backend | Frontend | Status |
|-------|------------|---------|----------|--------|
| **Phase 1** | Project Foundation | ✅ Spring Boot 4.1 + Java 17, PostgreSQL, Flyway (`V1`/`V2`), Base Exceptions | ✅ Theme system & common CSS reset/layout/utilities | **COMPLETED** |
| **Phase 2** | Security & Authentication | ✅ JJWT 0.12.6, SecurityConfig, LoginRateLimiter, BranchContext, DataInitializer | — | **COMPLETED** |
| **Phase 3** | Frontend Base | — | ✅ Multi-theme tokens (`white-black`, `dark`, `midnight`, etc.), layout & components | **COMPLETED** |
| **Phase 4** | Authentication & User Accounts | ✅ AuthController, RefreshToken rotation, User & Branch APIs | ✅ Login desk, User Accounts & Branches desks | **COMPLETED** |
| **Phase 5** | Employee Module | ✅ DTOs, Mapper, EmployeeService, EmployeeController | ✅ Employee desk (`employee.html`, search, filters, modals, Excel export) | **COMPLETED** |
| **Phase 6** | Customer Module | ✅ Mobile-keyed CustomerService & CustomerController | ✅ Customer desk (`customer.html`, search, detail modal, Excel export) | **COMPLETED** |
| **Phase 7** | Customer Order Module | ✅ OrderService, state machine, optimistic locking, sequence numbering | ✅ Order desk (`order.html`, intake form, measurement auto-fill, quick stage update) | **COMPLETED** |
| **Phase 8** | Design Module | ⏳ Table `order_designs` in `V1` schema | ⏳ Deferred to future phase | Deferred |
| **Phase 9** | Measurement Module | ✅ CustomerMeasurementService & OrderMeasurement snapshot | ✅ Measurement desk (`measurement.html`, Blouse/Chudi templates, spec modal, Excel export) | **COMPLETED** |
| **Phase 10** | Material Module | ⏳ Table `order_materials` in `V1` schema | ⏳ Deferred to future phase | Deferred |
| **Phase 11** | Production Module | ✅ 12 DB-driven `production_stages` & custom stage CRUD/reorder | ✅ Stage config (`stage.html`) & Production desks | **COMPLETED** |
| **Phase 12** | Payment Module | ✅ `order_payments` ledger, payment reconciliation & discounts | ✅ Payment ledger modals in Order & Delivery desks | **COMPLETED** |
| **Phase 13** | Delivery Module | ✅ Delivery transition guards, receiver tracking & archive cleanup | ✅ Delivery desk (`delivery.html`, Ready/Delivered tabs, Excel & PDF export) | **COMPLETED** |
| **Phase 14** | Dashboard 1 (Order Desk) | ✅ Branch-scoped `/api/orders` & `/stats` | ✅ Order Desk (`order-desk.html`, KPI cards, live filter tabs, Excel export) | **COMPLETED** |
| **Phase 15** | Dashboard 2 (Production Floor) | ✅ Stage filtering & worker stage assignment | ✅ Production Floor Desk (`production-desk.html` & `production.html`) | **COMPLETED** |
| **Phase 16** | Dashboard 3 (Operations Monitoring) | ✅ Stage KPIs & role-restricted operations views | ✅ Operations Desk (`operations-desk.html`, urgency view, non-financial Excel export) | **COMPLETED** |
| **Phase 17** | Reports & Analytics Module | ✅ `/api/orders/analytics` filtered aggregates & trends | ✅ Reports desk (`report.html`, Chart.js visualizations & Excel export) | **COMPLETED** |
| **Phase 18** | Historical Data Migration & Backups | ✅ PDF/Image OCR extraction (Tess4J/PDFBox), review/import, Backup & DR service/scripts | ✅ Migration desk (`migration.html`, camera/review modal) & Backup desk (`backup.html`) | **COMPLETED** |
| **Phase 19** | Verification & Production Deployment | ✅ JUnit 5 + MockMvc test suite, Flyway migrations, `prod` profile (`ForwardedHeaderFilter`) | ✅ Split GitHub Pages (`deploy-pages.yml`) + Cloudflare Tunnel setup | **COMPLETED** |
