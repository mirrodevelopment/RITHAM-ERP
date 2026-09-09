package com.ritham.erp.module.migration.service.ocr;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calculates field-level and overall confidence scores and evaluates the human-review requirement.
 */
@Component
public class ConfidenceService {

    public record ConfidenceEvaluation(
            Map<String, Object> confidenceMap,
            double overallConfidence,
            boolean needsReview
    ) {}

    public ConfidenceEvaluation evaluate(
            String garmentType, Double garmentConf,
            String customerName, Double nameConf,
            String orderId, Double orderIdConf,
            String mobileNo, Double mobileConf,
            String date, Double dateConf,
            String dueDate, Double dueDateConf,
            Map<String, String> measurements,
            Map<String, Double> measurementConfidences) {

        Map<String, Object> confMap = new LinkedHashMap<>();

        confMap.put("garmentType", garmentConf != null ? garmentConf : 0.0);
        confMap.put("customerName", nameConf != null ? nameConf : 0.0);
        confMap.put("orderId", orderIdConf != null ? orderIdConf : 0.0);
        confMap.put("mobileNo", mobileConf != null ? mobileConf : 0.0);
        confMap.put("date", dateConf != null ? dateConf : 0.0);
        confMap.put("dueDate", dueDateConf != null ? dueDateConf : 0.0);

        Map<String, Double> measConfs = new LinkedHashMap<>();
        if (measurementConfidences != null) {
            measConfs.putAll(measurementConfidences);
        }
        confMap.put("measurements", measConfs);

        // Calculate overall average confidence
        double sum = 0.0;
        int count = 0;
        for (Double c : List.of(
                garmentConf != null ? garmentConf : 0.0,
                nameConf != null ? nameConf : 0.0,
                orderIdConf != null ? orderIdConf : 0.0,
                mobileConf != null ? mobileConf : 0.0,
                dateConf != null ? dateConf : 0.0,
                dueDateConf != null ? dueDateConf : 0.0)) {
            sum += c;
            count++;
        }

        if (measConfs != null && !measConfs.isEmpty()) {
            for (Double mc : measConfs.values()) {
                sum += mc;
                count++;
            }
        }

        double overall = count > 0 ? Math.round((sum / count) * 100.0) / 100.0 : 0.0;

        // Needs review evaluation (strict rules as per Master Prompt)
        boolean needsReview = false;
        if (garmentType == null || "NEEDS_REVIEW".equals(garmentType) || garmentConf == null || garmentConf < 0.70) {
            needsReview = true;
        } else if (customerName == null || customerName.isBlank() || (nameConf != null && nameConf < 0.60)) {
            needsReview = true;
        } else if (mobileNo == null || mobileNo.isBlank()) {
            needsReview = true;
        } else if (orderId == null || orderId.isBlank()) {
            needsReview = true;
        } else if (measurements == null || measurements.size() < 4) {
            needsReview = true;
        }

        return new ConfidenceEvaluation(confMap, overall, needsReview);
    }
}
