package com.ritham.erp.module.migration.service.ocr;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Central normalization layer for OCR extracted fields.
 * Enforces canonical representation across garment types and strips prohibited fields (e.g. cloth).
 */
@Component
public class FieldNormalizer {

    /**
     * Normalizes measurement labels and values according to Ritham ERP specifications.
     */
    public Map<String, String> normalizeMeasurements(String garmentType, Map<String, String> rawMeasurements) {
        if (rawMeasurements == null) return new LinkedHashMap<>();
        Map<String, String> normalized = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : rawMeasurements.entrySet()) {
            String key = entry.getKey();
            String val = entry.getValue();
            if (key == null || val == null) continue;

            // Strip cloth if erroneously captured
            if ("CLOTH".equalsIgnoreCase(key) || "FABRIC".equalsIgnoreCase(key)) {
                continue;
            }

            String normKey = normalizeLabel(garmentType, key);
            String normVal = normalizeNumericValue(val);

            if (normVal != null && !normVal.isBlank()) {
                normalized.put(normKey, normVal);
            }
        }

        return normalized;
    }

    public String normalizeLabel(String garmentType, String label) {
        if (label == null) return "";
        String u = label.trim().toUpperCase();

        if ("CHUDI".equalsIgnoreCase(garmentType)) {
            if ("F.N.".equals(u) || "F.N".equals(u)) return "FN";
            if ("B.N.".equals(u) || "B.N".equals(u)) return "BN";
            if ("H.B.".equals(u) || "H.B".equals(u)) return "HB";
            if ("T.S.".equals(u) || "T.S".equals(u)) return "TS";
            if ("P.L.".equals(u) || "P.L".equals(u) || "PL.".equals(u)) return "PL";
            if ("S.CUT".equals(u)) return "SCUT";
            if ("B.D.".equals(u) || "B.D".equals(u)) return "BD";
            if ("S.".equals(u)) return "S";
            if ("L.".equals(u)) return "L-2";
        } else if ("BLOUSE".equalsIgnoreCase(garmentType)) {
            // Preserve distinct H.S., H.L., H.LO
            if ("HS".equals(u)) return "H.S.";
            if ("HL".equals(u)) return "H.L.";
            if ("HLO".equals(u)) return "H.LO";
            if ("DP1_1".equals(u) || "DP-1".equals(u)) return "DP-1-1";
            if ("DP1_2".equals(u)) return "DP-1-2";
            if ("B1".equals(u)) return "B-1";
            if ("B2".equals(u)) return "B-2";
            if ("B3".equals(u)) return "B-3";
            if ("F_HOOK".equals(u)) return "F-HOOK";
            if ("B_HOOK".equals(u)) return "B-HOOK";
            if ("AV".equals(u)) return "AV.";
        }

        return u;
    }

    public String normalizeNumericValue(String val) {
        if (val == null) return null;
        String s = val.strip().replaceAll("[^0-9.]", "");
        if (s.isEmpty()) return null;
        try {
            double d = Double.parseDouble(s);
            if (d >= 0 && d <= 999) {
                // If it's an integer, return without trailing .0
                if (d == (long) d) {
                    return String.valueOf((long) d);
                }
                return s;
            }
        } catch (NumberFormatException ignored) {}
        return null;
    }

    public String normalizeMobile(String mobile) {
        if (mobile == null) return null;
        String digits = mobile.replaceAll("[^\\d]", "");
        return digits.matches("^[6-9]\\d{9}$") ? digits : null;
    }
}
