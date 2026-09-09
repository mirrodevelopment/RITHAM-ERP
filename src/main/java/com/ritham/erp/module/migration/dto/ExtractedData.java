package com.ritham.erp.module.migration.dto;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Structured data extracted from OCR text.
 * Represents the standardized Section 16 format:
 * customerName, mobileNo, orderId, date, dueDate, garmentType, measurements, confidence, needsReview.
 * Also maintains customer() and order() record accessors for internal ERP processing.
 */
public record ExtractedData(
        String customerName,
        String mobileNo,
        String orderId,
        String date,
        String dueDate,
        String garmentType,
        Map<String, String> measurements,
        Map<String, Object> confidence,
        Boolean needsReview,
        ExtractedCustomerData customer,
        ExtractedOrderData order,
        Double overallConfidence,
        Map<String, RegionBox> regions,
        String orientation
) {

    public ExtractedData {
        if (customer == null && (customerName != null || mobileNo != null)) {
            double cNameConf = 0.85;
            double cMobConf = mobileNo != null ? 0.97 : 0.0;
            customer = new ExtractedCustomerData(customerName, cNameConf, mobileNo, cMobConf, needsReview != null ? needsReview : false);
        } else if (customer != null) {
            if (customerName == null) customerName = customer.customerName();
            if (mobileNo == null) mobileNo = customer.customerMobile();
        }

        if (order == null && (garmentType != null || measurements != null || orderId != null || date != null)) {
            order = new ExtractedOrderData(
                    date, date != null ? 0.82 : 0.0,
                    dueDate, dueDate != null ? 0.80 : 0.0,
                    garmentType != null ? garmentType : "NEEDS_REVIEW", garmentType != null ? 0.95 : 0.0,
                    "WITHOUT_LINING",
                    measurements != null ? measurements : Map.of(),
                    Map.of(),
                    null, 0.0,
                    null, 0.0,
                    false,
                    needsReview != null ? needsReview : false,
                    orderId,
                    orderId != null ? 0.98 : 0.0
            );
        } else if (order != null) {
            if (orderId == null) orderId = order.orderId();
            if (date == null) date = order.orderDate();
            if (dueDate == null) dueDate = order.deliveryDate();
            if (garmentType == null) garmentType = order.garmentType();
            if (measurements == null) measurements = order.measurements();
        }

        if (measurements == null) measurements = Map.of();
        if (confidence == null) confidence = buildConfidenceMap(customer, order);
        if (needsReview == null) {
            needsReview = (customer != null && Boolean.TRUE.equals(customer.needsReview())) ||
                          (order != null && Boolean.TRUE.equals(order.needsReview()));
        }
        if (orientation == null) orientation = "PORTRAIT";
        if (overallConfidence == null) overallConfidence = 0.0;
    }

    public ExtractedData(ExtractedCustomerData customer, ExtractedOrderData order, Double overallConfidence) {
        this(customer, order, overallConfidence, null, "PORTRAIT");
    }

    public ExtractedData(
            ExtractedCustomerData customer,
            ExtractedOrderData order,
            Double overallConfidence,
            Map<String, RegionBox> regions,
            String orientation
    ) {
        this(
                customer != null ? customer.customerName() : null,
                customer != null ? customer.customerMobile() : null,
                order != null ? order.orderId() : null,
                order != null ? order.orderDate() : null,
                order != null ? order.deliveryDate() : null,
                order != null ? order.garmentType() : null,
                order != null ? order.measurements() : Map.of(),
                buildConfidenceMap(customer, order),
                (customer != null && Boolean.TRUE.equals(customer.needsReview())) ||
                        (order != null && Boolean.TRUE.equals(order.needsReview())),
                customer,
                order,
                overallConfidence,
                regions,
                orientation != null ? orientation : "PORTRAIT"
        );
    }

    private static Map<String, Object> buildConfidenceMap(ExtractedCustomerData customer, ExtractedOrderData order) {
        Map<String, Object> conf = new LinkedHashMap<>();
        if (customer != null) {
            if (customer.customerName() != null) conf.put("customerName", customer.nameConfidence());
            if (customer.customerMobile() != null) conf.put("mobileNo", customer.mobileConfidence());
        }
        if (order != null) {
            if (order.orderId() != null) conf.put("orderId", order.orderIdConfidence() != null ? order.orderIdConfidence() : 0.98);
            if (order.orderDate() != null) conf.put("date", order.orderDateConfidence());
            if (order.deliveryDate() != null) conf.put("dueDate", order.deliveryDateConfidence());
            if (order.garmentType() != null) conf.put("garmentType", order.garmentTypeConfidence());
            if (order.measurementConfidences() != null) conf.put("measurements", order.measurementConfidences());
        }
        return conf;
    }

    public record RegionBox(
            double x,
            double y,
            double w,
            double h,
            String color,
            String label,
            java.util.List<String> fields
    ) {}

    public record ExtractedCustomerData(
            String customerName,
            Double nameConfidence,
            String customerMobile,
            Double mobileConfidence,
            Boolean needsReview
    ) {}

    public record ExtractedOrderData(
            String orderDate,           // ISO: 2024-09-05 (null if unparseable)
            Double orderDateConfidence,
            String deliveryDate,        // ISO: 2024-09-12 (null if unparseable)
            Double deliveryDateConfidence,
            String garmentType,         // BLOUSE | CHUDI
            Double garmentTypeConfidence,
            String lining,              // WITH_LINING | WITHOUT_LINING (default: WITHOUT_LINING)
            Map<String, String> measurements,
            Map<String, Double> measurementConfidences,
            BigDecimal totalAmount,
            Double totalAmountConfidence,
            BigDecimal advanceAmount,
            Double advanceAmountConfidence,
            Boolean paymentDateKnown,   // false when exact payment date is missing from paper
            Boolean needsReview,        // true if any field is below threshold
            String orderId,             // Physical form Order ID (previously printed as Erode)
            Double orderIdConfidence
    ) {
        public ExtractedOrderData(
                String orderDate, Double orderDateConfidence,
                String deliveryDate, Double deliveryDateConfidence,
                String garmentType, Double garmentTypeConfidence,
                String lining,
                Map<String, String> measurements, Map<String, Double> measurementConfidences,
                BigDecimal totalAmount, Double totalAmountConfidence,
                BigDecimal advanceAmount, Double advanceAmountConfidence,
                Boolean paymentDateKnown, Boolean needsReview,
                String orderId
        ) {
            this(orderDate, orderDateConfidence, deliveryDate, deliveryDateConfidence,
                 garmentType, garmentTypeConfidence, lining,
                 measurements, measurementConfidences,
                 totalAmount, totalAmountConfidence, advanceAmount, advanceAmountConfidence,
                 paymentDateKnown, needsReview, orderId, orderId != null ? 0.98 : 0.0);
        }

        // Backward-compatibility alias
        public String erode() { return orderId; }
    }
}

