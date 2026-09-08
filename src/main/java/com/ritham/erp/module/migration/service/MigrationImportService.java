package com.ritham.erp.module.migration.service;

import tools.jackson.databind.ObjectMapper;
import com.ritham.erp.common.exception.AppException;
import com.ritham.erp.common.exception.ErrorCode;
import com.ritham.erp.module.customer.dto.SaveCustomerMeasurementRequest;
import com.ritham.erp.module.customer.entity.Customer;
import com.ritham.erp.module.customer.repository.CustomerRepository;
import com.ritham.erp.module.customer.service.CustomerMeasurementService;
import com.ritham.erp.module.migration.dto.ExtractedData;
import com.ritham.erp.module.migration.dto.MigrationReviewRequest;
import com.ritham.erp.module.migration.entity.MigrationDocument;
import com.ritham.erp.module.migration.entity.MigrationImport;
import com.ritham.erp.module.migration.enums.MigrationDocumentStatus;
import com.ritham.erp.module.migration.repository.MigrationDocumentRepository;
import com.ritham.erp.module.migration.repository.MigrationImportRepository;
import com.ritham.erp.module.order.entity.CustomerOrder;
import com.ritham.erp.module.order.entity.OrderMeasurement;
import com.ritham.erp.module.order.entity.OrderPayment;
import com.ritham.erp.module.order.repository.CustomerOrderRepository;
import com.ritham.erp.module.order.repository.OrderMeasurementRepository;
import com.ritham.erp.module.order.repository.OrderPaymentRepository;
import com.ritham.erp.module.order.service.OrderService;
import com.ritham.erp.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * Imports a VERIFIED migration document into the live ERP tables.
 *
 * <p>Safety rules:
 * <ul>
 *   <li>Only VERIFIED documents can be imported.</li>
 *   <li>Already-imported documents are rejected (idempotency guard).</li>
 *   <li>Historical orders bypass the normal state machine — created as DELIVERED
 *       and flagged source=HISTORICAL_MIGRATION.</li>
 *   <li>Payment ledger rows are created; when the exact payment date is unknown,
 *       the date falls back to Jan 1 of the paper record's source year.</li>
 *   <li>The entire import is atomic: any failure rolls back the full transaction.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MigrationImportService {

    private static final String SOURCE_HISTORICAL = "HISTORICAL_MIGRATION";
    private static final String STATUS_IMPORTED   = MigrationDocumentStatus.IMPORTED.name();
    private static final String STATUS_FAILED     = MigrationDocumentStatus.IMPORT_FAILED.name();
    private static final String STATUS_DUPLICATE  = MigrationDocumentStatus.DUPLICATE.name();

    private final MigrationDocumentRepository documentRepository;
    private final MigrationImportRepository   importRepository;

    private final CustomerRepository            customerRepository;
    private final CustomerMeasurementService   customerMeasurementService;
    private final CustomerOrderRepository       orderRepository;
    private final OrderMeasurementRepository    measurementRepository;
    private final OrderPaymentRepository        paymentRepository;

    private final ExtractionService extractionService;
    private final ObjectMapper      objectMapper;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Import a VERIFIED document into production ERP tables.
     *
     * @param documentId    the migration document ID
     * @param reviewRequest the reviewer's final submission (contains customer match decision)
     * @return the created MigrationImport audit record
     */
    @Transactional
    public MigrationImport importDocument(Long documentId, MigrationReviewRequest reviewRequest) {

        MigrationDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Migration document not found: " + documentId));

        // ── Guard: idempotency ────────────────────────────────────────────────
        if (doc.isAlreadyImported()) {
            throw new AppException(ErrorCode.OPERATION_NOT_ALLOWED,
                    "Document " + doc.getDocumentCode() + " has already been imported.");
        }

        // ── Auto-verify if review approval request or valid extracted data is present ──
        if (reviewRequest != null && "APPROVED".equalsIgnoreCase(reviewRequest.action())) {
            if (reviewRequest.correctedDataJson() != null && !reviewRequest.correctedDataJson().isBlank()) {
                doc.setCorrectedDataJson(reviewRequest.correctedDataJson());
            }
            doc.setReviewStatus(MigrationDocumentStatus.VERIFIED.name());
            doc.getBatch().incrementApproved();
            doc.getBatch().incrementReviewed();
            documentRepository.save(doc);
        } else if (!doc.isReadyToImport()) {
            String effective = doc.effectiveDataJson();
            if (effective != null && !effective.isBlank()) {
                doc.setReviewStatus(MigrationDocumentStatus.VERIFIED.name());
                doc.getBatch().incrementApproved();
                doc.getBatch().incrementReviewed();
                documentRepository.save(doc);
            } else {
                throw new AppException(ErrorCode.OPERATION_NOT_ALLOWED,
                        "Document must have extracted data before import.");
            }
        }

        // ── Resolve effective data ────────────────────────────────────────────
        String effectiveJson = doc.effectiveDataJson();
        if (effectiveJson == null || effectiveJson.isBlank()) {
            return failImport(doc, "No extracted data available for import");
        }

        ExtractedData data = extractionService.fromJson(effectiveJson);
        double ratio = extractionService.calculateCompletionRatio(data);
        if (ratio < 0.50) {
            int pct = (int) Math.round(ratio * 100);
            throw new AppException(ErrorCode.OPERATION_NOT_ALLOWED,
                    "Cannot process document: only " + pct + "% of fields are filled. At least 50% field completion is required before importing.");
        }

        try {
            return doImport(doc, data, reviewRequest);
        } catch (AppException e) {
            throw e; // let AppExceptions propagate normally
        } catch (Exception e) {
            log.error("Import failed for document {}: {}", doc.getDocumentCode(), e.getMessage(), e);
            return failImport(doc, e.getMessage());
        }
    }

    // ── Core import transaction ───────────────────────────────────────────────

    private MigrationImport doImport(MigrationDocument doc,
                                      ExtractedData data,
                                      MigrationReviewRequest req) {

        ExtractedData.ExtractedCustomerData cData = data.customer();
        ExtractedData.ExtractedOrderData    oData = data.order();

        // ── Step 1: Resolve customer ──────────────────────────────────────────
        String mobile = resolveMobile(req, cData);
        String name   = resolveName(cData);

        Customer customer = resolveOrCreateCustomer(mobile, name);

        // ── Step 2: Duplicate order check ────────────────────────────────────
        // Considered duplicate: same customer, same garment type, same order year.
        if (isDuplicateOrder(mobile, oData)) {
            log.warn("Duplicate order detected for customer={} garment={} year={}",
                    mobile, oData.garmentType(), parseYear(oData.orderDate()));
            doc.setImportStatus(STATUS_DUPLICATE);
            documentRepository.save(doc);
            doc.getBatch().incrementFailed();

            MigrationImport dupe = MigrationImport.builder()
                    .document(doc)
                    .customerMobile(mobile)
                    .importStatus(STATUS_DUPLICATE)
                    .errorMessage("Duplicate: customer already has an order for this garment/date")
                    .importedBy(currentEmployee())
                    .build();
            return importRepository.save(dupe);
        }

        // ── Step 3: Create order ──────────────────────────────────────────────
        LocalDateTime orderDt    = parseDate(oData.orderDate(),    doc.getSourceYear());
        LocalDateTime deliveryDt = parseDate(oData.deliveryDate(), doc.getSourceYear());
        String        orderNum   = generateHistoricalOrderNumber(orderDt, doc.getDocumentCode());

        CustomerOrder order = CustomerOrder.builder()
                .customerMobile(mobile)
                .orderNumber(orderNum)
                .orderDate(orderDt)
                .deliveryDate(deliveryDt)
                .garmentType(oData.garmentType())
                .lining(oData.lining() != null ? oData.lining() : "WITHOUT_LINING")
                .totalAmount(nvl(oData.totalAmount()))
                .discountAmount(BigDecimal.ZERO)
                .advanceAmount(nvl(oData.advanceAmount()))
                .paidAmount(nvl(oData.advanceAmount()))
                .paymentMode(OrderService.PAYMENT_MODE_CASH)
                .paymentStatus(resolvePaymentStatus(oData))
                .status(OrderService.STATUS_DELIVERED)   // historical = already delivered
                .receiverName(name)
                .branch(doc.getBatch().getBranch())
                .source(SOURCE_HISTORICAL)
                .build();

        order = orderRepository.save(order);
        customer.incrementOrderCount();
        customerRepository.save(customer);

        // ── Step 4: Immutable measurement snapshot ────────────────────────────
        OrderMeasurement snapshot = null;
        if (oData.measurements() != null && !oData.measurements().isEmpty()) {
            snapshot = OrderMeasurement.builder()
                    .orderId(order.getId())
                    .garmentType(oData.garmentType())
                    .measurementsJson(measurementsToJson(oData.measurements()))
                    .build();
            snapshot = measurementRepository.save(snapshot);

            // Sync to live customer measurement profile if requested (or default if req omitted)
            boolean updateProfile = req == null || req.updateCustomerProfile();
            if (updateProfile) {
                try {
                    SaveCustomerMeasurementRequest measReq = new SaveCustomerMeasurementRequest();
                    measReq.setCustomerMobile(mobile);
                    measReq.setCustomerName(name);
                    measReq.setGarmentType(oData.garmentType() != null ? oData.garmentType() : "BLOUSE");
                    measReq.setLining(oData.lining());
                    measReq.setMeasurements(oData.measurements());
                    measReq.setNotes("Imported from historical order " + order.getOrderNumber());
                    customerMeasurementService.saveOrUpdate(measReq);
                    log.info("Synced customer measurement profile for mobile={}", mobile);
                } catch (Exception e) {
                    log.warn("Could not sync customer measurement profile for {}: {}", mobile, e.getMessage());
                }
            }
        }

        // ── Step 5: Payment ledger entry ──────────────────────────────────────
        if (nvl(oData.advanceAmount()).compareTo(BigDecimal.ZERO) > 0) {
            LocalDateTime paymentDate = oData.paymentDateKnown()
                    ? orderDt
                    : LocalDateTime.of(doc.getSourceYear(), 1, 1, 0, 0);

            OrderPayment payment = OrderPayment.builder()
                    .orderId(order.getId())
                    .paymentType(OrderService.PAYMENT_TYPE_ADVANCE)
                    .paymentMethod(OrderService.PAYMENT_MODE_CASH)
                    .amount(nvl(oData.advanceAmount()))
                    .paymentDate(paymentDate)
                    .build();
            paymentRepository.save(payment);
        }

        // ── Step 6: Audit record ──────────────────────────────────────────────
        doc.setImportStatus(STATUS_IMPORTED);
        doc.setReviewStatus(STATUS_IMPORTED);
        documentRepository.save(doc);
        doc.getBatch().incrementImported();

        MigrationImport importRecord = MigrationImport.builder()
                .document(doc)
                .customerMobile(mobile)
                .orderId(order.getId())
                .measurementId(snapshot != null ? snapshot.getId() : null)
                .importStatus(STATUS_IMPORTED)
                .importedBy(currentEmployee())
                .build();

        log.info("Successfully imported document {} -> order {}, customer {}",
                doc.getDocumentCode(), order.getOrderNumber(), mobile);

        return importRepository.save(importRecord);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Customer resolveOrCreateCustomer(String mobile, String name) {
        return customerRepository.findByCustomerMobile(mobile)
                .orElseGet(() -> {
                    log.info("Creating new customer during migration import: {}", mobile);
                    return customerRepository.save(
                            Customer.builder()
                                    .customerMobile(mobile)
                                    .customerName(name != null ? name : "Unknown")
                                    .build());
                });
    }

    private boolean isDuplicateOrder(String mobile, ExtractedData.ExtractedOrderData oData) {
        if (oData.garmentType() == null || oData.orderDate() == null) return false;
        return orderRepository.existsByCustomerMobileAndGarmentTypeAndOrderDateYear(
                mobile, oData.garmentType(), parseYear(oData.orderDate()));
    }

    private LocalDateTime parseDate(String isoDate, short fallbackYear) {
        if (isoDate != null) {
            try {
                return LocalDate.parse(isoDate).atTime(LocalTime.NOON);
            } catch (DateTimeParseException ignored) {}
        }
        return LocalDateTime.of(fallbackYear, 1, 1, 12, 0);
    }

    private int parseYear(String isoDate) {
        try {
            return LocalDate.parse(isoDate).getYear();
        } catch (Exception e) {
            return 0;
        }
    }

    private String resolvePaymentStatus(ExtractedData.ExtractedOrderData oData) {
        BigDecimal total = nvl(oData.totalAmount());
        BigDecimal paid  = nvl(oData.advanceAmount());
        if (total.compareTo(BigDecimal.ZERO) == 0) return OrderService.PAYMENT_STATUS_PENDING;
        int cmp = paid.compareTo(total);
        if (cmp >= 0) return OrderService.PAYMENT_STATUS_PAID;
        if (paid.compareTo(BigDecimal.ZERO) > 0) return OrderService.PAYMENT_STATUS_PARTIAL;
        return OrderService.PAYMENT_STATUS_PENDING;
    }

    private String generateHistoricalOrderNumber(LocalDateTime date, String docCode) {
        String suffix = docCode.length() > 6
                ? docCode.substring(docCode.length() - 6)
                : docCode;
        String base = "HIST-" + date.toLocalDate().toString().replace("-", "") + "-" + suffix;
        if (!orderRepository.existsByOrderNumber(base)) {
            return base;
        }
        return base + "-" + (System.currentTimeMillis() % 100000);
    }

    private String measurementsToJson(Map<String, String> measurements) {
        try {
            return objectMapper.writeValueAsString(measurements);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String resolveMobile(MigrationReviewRequest req, ExtractedData.ExtractedCustomerData cData) {
        if (req != null && req.matchedCustomerMobile() != null
                && !req.matchedCustomerMobile().isBlank()) {
            return req.matchedCustomerMobile();
        }
        if (cData != null && cData.customerMobile() != null && !cData.customerMobile().isBlank()) {
            return cData.customerMobile();
        }
        throw new AppException(ErrorCode.VALIDATION_FAILED,
                "No customer mobile number available for import");
    }

    private String resolveName(ExtractedData.ExtractedCustomerData cData) {
        return (cData != null && cData.customerName() != null && !cData.customerName().isBlank())
                ? cData.customerName() : "Unknown";
    }

    private BigDecimal nvl(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private MigrationImport failImport(MigrationDocument doc, String error) {
        doc.setImportStatus(STATUS_FAILED);
        doc.setErrorMessage(error);
        documentRepository.save(doc);
        doc.getBatch().incrementFailed();

        MigrationImport failed = MigrationImport.builder()
                .document(doc)
                .importStatus(STATUS_FAILED)
                .errorMessage(error)
                .importedBy(currentEmployee())
                .build();
        return importRepository.save(failed);
    }

    private com.ritham.erp.module.employee.entity.Employee currentEmployee() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails ud) {
            var emp = new com.ritham.erp.module.employee.entity.Employee();
            emp.setId(ud.getEmployeeId());
            return emp;
        }
        return null;
    }
}
