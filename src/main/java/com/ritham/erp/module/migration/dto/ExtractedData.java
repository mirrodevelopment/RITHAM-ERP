package com.ritham.erp.module.migration.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Structured data extracted from OCR text.
 * Every field carries a {@code confidence} score (0.0 – 1.0).
 * Fields with confidence below the configured threshold are flagged
 * {@code needsReview = true} for the human reviewer.
 */
public record ExtractedData(

        ExtractedCustomerData customer,
        ExtractedOrderData    order,

        /** Overall document confidence (average of all field confidences). */
        Double overallConfidence,

        /** Dynamic text-aligned annotation regions keyed by region name */
        Map<String, RegionBox> regions,

        /** Detected image orientation: PORTRAIT or LANDSCAPE */
        String orientation

) {

    public ExtractedData(ExtractedCustomerData customer, ExtractedOrderData order, Double overallConfidence) {
        this(customer, order, overallConfidence, null, "PORTRAIT");
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
            String orderDate,           // ISO: 2023-03-12 (null if unparseable)
            Double orderDateConfidence,
            String deliveryDate,        // ISO: 2023-03-18 (null if unparseable)
            Double deliveryDateConfidence,
            String garmentType,         // BLOUSE | CHUDI | SAREE | SALWAR | LEHENGA | OTHER
            Double garmentTypeConfidence,
            String lining,              // WITH_LINING | WITHOUT_LINING (default: WITHOUT_LINING)
            Map<String, String> measurements,         // e.g. {"FN": "14", "SL_1": "11", "SL_2": "11"}
            Map<String, Double> measurementConfidences,
            BigDecimal totalAmount,
            Double totalAmountConfidence,
            BigDecimal advanceAmount,
            Double advanceAmountConfidence,
            Boolean paymentDateKnown,   // false when exact payment date is missing from paper
            Boolean needsReview,        // true if any field is below threshold
            String erode,               // Staging-only: Erode reference number from physical form header
            String cloth                // Staging-only: Cloth value from header (null if blank)
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
                String erode
        ) {
            this(orderDate, orderDateConfidence, deliveryDate, deliveryDateConfidence,
                 garmentType, garmentTypeConfidence, lining,
                 measurements, measurementConfidences,
                 totalAmount, totalAmountConfidence, advanceAmount, advanceAmountConfidence,
                 paymentDateKnown, needsReview, erode, null);
        }
    }
}

