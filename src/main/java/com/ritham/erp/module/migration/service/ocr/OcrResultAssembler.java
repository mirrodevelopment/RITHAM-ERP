package com.ritham.erp.module.migration.service.ocr;

import com.ritham.erp.module.migration.dto.ExtractedData;
import com.ritham.erp.module.migration.service.OcrService.WordBox;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Assembles header extractions, measurement extractions, confidence scoring, and color-coded
 * SVG bounding box coordinates into the Section 16 compliant {@link ExtractedData} object.
 */
@Component
public class OcrResultAssembler {

    public static final String COLOR_BLUE   = "#2563eb"; // Customer Name
    public static final String COLOR_GREEN  = "#16a34a"; // Order ID (Erode)
    public static final String COLOR_PURPLE = "#9333ea"; // Garment Type
    public static final String COLOR_RED    = "#dc2626"; // Mobile, Dates
    public static final String COLOR_ORANGE = "#ea580c"; // Measurements

    public ExtractedData assemble(
            HeaderExtractionService.HeaderFields headers,
            FormTemplateDetector.GarmentTypeResult garmentResult,
            MeasurementExtractionService.MeasurementResult measurementResult,
            ConfidenceService.ConfidenceEvaluation confidenceEval,
            String orientation) {

        Map<String, ExtractedData.RegionBox> regions = new LinkedHashMap<>();

        // ── 1. Top-Left Regions (Blue / Green) ─────────────────────────────────
        List<WordBox> customerBoxes = new ArrayList<>();

        if (headers.customerNameBox() != null) {
            WordBox b = headers.customerNameBox();
            regions.put("field_revCustomerName", toRegionBox(b, COLOR_BLUE, "Customer Name", List.of("revCustomerName")));
            customerBoxes.add(b);
        } else {
            regions.put("field_revCustomerName", new ExtractedData.RegionBox(0.08, 0.04, 0.35, 0.06, COLOR_BLUE, "Customer Name", List.of("revCustomerName")));
        }

        if (headers.orderIdBox() != null) {
            WordBox b = headers.orderIdBox();
            regions.put("field_revOrderId", toRegionBox(b, COLOR_GREEN, "Order ID", List.of("revOrderId")));
            customerBoxes.add(b);
        } else {
            regions.put("field_revOrderId", new ExtractedData.RegionBox(0.08, 0.10, 0.20, 0.05, COLOR_GREEN, "Order ID", List.of("revOrderId")));
        }

        regions.put("customerRegion", new ExtractedData.RegionBox(
                0.06, 0.03, 0.38, 0.15,
                COLOR_BLUE, "Customer Info (Name / Order ID)", List.of("revCustomerName", "revOrderId")));

        // ── 2. Top-Center Garment Region (Purple) ──────────────────────────────
        if (garmentResult.boundingBox() != null) {
            WordBox b = garmentResult.boundingBox();
            regions.put("field_revGarmentType", toRegionBox(b, COLOR_PURPLE, "Garment Type", List.of("revGarmentType")));
            regions.put("garmentRegion", toRegionBox(b, COLOR_PURPLE, "Garment Type", List.of("revGarmentType")));
        } else {
            ExtractedData.RegionBox gBox = new ExtractedData.RegionBox(0.38, 0.02, 0.22, 0.07, COLOR_PURPLE, "Garment Type", List.of("revGarmentType"));
            regions.put("field_revGarmentType", gBox);
            regions.put("garmentRegion", gBox);
        }

        // ── 3. Top-Right Phone & Dates Regions (Red) ───────────────────────────
        if (headers.mobileNoBox() != null) {
            regions.put("field_revCustomerMobile", toRegionBox(headers.mobileNoBox(), COLOR_RED, "Mobile", List.of("revCustomerMobile")));
        } else {
            regions.put("field_revCustomerMobile", new ExtractedData.RegionBox(0.60, 0.12, 0.32, 0.05, COLOR_RED, "Mobile", List.of("revCustomerMobile")));
        }

        if (headers.dateBox() != null) {
            regions.put("field_revOrderDate", toRegionBox(headers.dateBox(), COLOR_RED, "Order Date", List.of("revOrderDate")));
        } else {
            regions.put("field_revOrderDate", new ExtractedData.RegionBox(0.60, 0.03, 0.32, 0.05, COLOR_RED, "Order Date", List.of("revOrderDate")));
        }

        if (headers.dueDateBox() != null) {
            regions.put("field_revDeliveryDate", toRegionBox(headers.dueDateBox(), COLOR_RED, "Due Date", List.of("revDeliveryDate")));
        } else {
            regions.put("field_revDeliveryDate", new ExtractedData.RegionBox(0.60, 0.07, 0.32, 0.05, COLOR_RED, "Due Date", List.of("revDeliveryDate")));
        }

        regions.put("dateMobileRegion", new ExtractedData.RegionBox(
                0.58, 0.02, 0.38, 0.16,
                COLOR_RED, "Phone & Dates", List.of("revCustomerMobile", "revOrderDate", "revDeliveryDate")));

        // ── 4. Measurement Column Regions (Orange) ─────────────────────────────
        if (measurementResult.regions() != null) {
            regions.putAll(measurementResult.regions());
        }

        // Legacy compatibility objects for ERP services
        ExtractedData.ExtractedCustomerData custDto = new ExtractedData.ExtractedCustomerData(
                headers.customerName(), headers.customerNameConfidence(),
                headers.mobileNo(), headers.mobileNoConfidence(),
                confidenceEval.needsReview()
        );

        Map<String, Double> measConfs = new LinkedHashMap<>();
        if (measurementResult.confidences() != null) {
            measConfs.putAll(measurementResult.confidences());
        }

        ExtractedData.ExtractedOrderData orderDto = new ExtractedData.ExtractedOrderData(
                headers.date(), headers.dateConfidence(),
                headers.dueDate(), headers.dueDateConfidence(),
                garmentResult.garmentType(), garmentResult.confidence(),
                "WITHOUT_LINING",
                measurementResult.measurements(),
                measConfs,
                null, 0.0,
                null, 0.0,
                false,
                confidenceEval.needsReview(),
                headers.orderId(), headers.orderIdConfidence()
        );

        return new ExtractedData(
                headers.customerName(),
                headers.mobileNo(),
                headers.orderId(),
                headers.date(),
                headers.dueDate(),
                garmentResult.garmentType(),
                measurementResult.measurements(),
                confidenceEval.confidenceMap(),
                confidenceEval.needsReview(),
                custDto,
                orderDto,
                confidenceEval.overallConfidence(),
                regions,
                orientation != null ? orientation : "PORTRAIT"
        );
    }

    private ExtractedData.RegionBox toRegionBox(WordBox b, String color, String label, List<String> fields) {
        return new ExtractedData.RegionBox(
                Math.round(b.x() * 1000.0) / 1000.0,
                Math.round(b.y() * 1000.0) / 1000.0,
                Math.round(b.width() * 1000.0) / 1000.0,
                Math.round(b.height() * 1000.0) / 1000.0,
                color,
                label,
                fields
        );
    }
}
