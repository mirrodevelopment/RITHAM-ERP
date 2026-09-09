package com.ritham.erp.module.migration.service.form;

import com.ritham.erp.module.migration.service.OcrService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Form definition for BLOUSE measurement slips.
 *
 * <p>The physical BLOUSE form layout (read in correct portrait orientation):
 * <pre>
 *  Header (right side of printed book):
 *    Name:       [handwritten]
 *    ERODE:      [number]
 *    CLOTH:      [handwritten]
 *    Ph.:        [10-digit mobile]
 *    Or. Date:   [DD/MM/YYYY]
 *    Due Date:   [DD/MM/YYYY]
 *    BLOUSE      ← garment type heading
 *
 *  Measurement column (left side, top-to-bottom):
 *    LTH   [value]
 *    SHO   [value]
 *    H.S.  [value]
 *    H.L.  [value]
 *    H.LO  [value]
 *    AK    [value]
 *    AM    [value]
 *    BN    [value]
 *    FN    [value]
 *    DP-1  [value]   ← internal key: DP1_1
 *    DP-1  [value]   ← internal key: DP1_2  (DUPLICATE printed label)
 *    B-1   [value]   ← internal key: B1
 *    B-2   [value]   ← internal key: B2
 *    B-3   [value]   ← internal key: B3
 *    F.HOOK [value]  ← internal key: F_HOOK
 *    B.HOOK [value]  ← internal key: B_HOOK
 *    LINING [value]
 *    AV.   [value]   ← internal key: AV
 *    SARI  [value]
 * </pre>
 *
 * <p>Key naming rules (spec-mandated):
 * <ul>
 *   <li>Dots/hyphens removed from internal keys: H.S. → HS, H.L. → HL, H.LO → HLO</li>
 *   <li>Duplicate DP-1 rows: DP1_1 and DP1_2</li>
 *   <li>Bust rows: B1, B2, B3 (no hyphens)</li>
 *   <li>Hook rows: F_HOOK, B_HOOK (underscore)</li>
 *   <li>Aari/Work: AV (no trailing dot)</li>
 * </ul>
 */
@Component
@Slf4j
public class BlouseFormDefinition implements MeasurementFormDefinition {

    // ── Canonical key list (spec order, matches physical form top-to-bottom) ──

    private static final List<String> KEYS = List.of(
            "LTH", "SHO", "H.S.", "H.L.", "H.LO",
            "AK", "AM", "BN", "FN",
            "DP-1-1", "DP-1-2",
            "B-1", "B-2", "B-3",
            "F-HOOK", "B-HOOK",
            "LINING", "AV.", "SARI"
    );

    // ── Alias map: OCR label → canonical key ──────────────────────────────────

    private static final Map<String, String> ALIASES;
    static {
        Map<String, String> m = new LinkedHashMap<>();

        // LTH — Length
        m.put("LTH",           "LTH");  m.put("LEN",          "LTH");
        m.put("LENGTH",        "LTH");  m.put("BLOUSELENGTH",  "LTH");
        m.put("LT",            "LTH");  m.put("ITH",           "LTH");

        // SHO — Shoulder
        m.put("SHO",           "SHO");  m.put("SH",           "SHO");
        m.put("SHOULDER",      "SHO");  m.put("SHOUL",        "SHO");

        // H.S. — Half Shoulder (printed H.S.)
        m.put("HS",            "H.S."); m.put("H.S.",         "H.S.");
        m.put("H.S",           "H.S."); m.put("HALFSHOULDER", "H.S.");
        m.put("HALFSHO",       "H.S."); m.put("HS.",          "H.S.");

        // H.L. — Hand Length / Sleeve Length (printed H.L.)
        m.put("HL",            "H.L."); m.put("H.L.",         "H.L.");
        m.put("H.L",           "H.L."); m.put("HANDLENGTH",   "H.L.");
        m.put("SLEEVELENGTH",  "H.L."); m.put("HL.",          "H.L.");
        m.put("HANDLEN",       "H.L.");

        // H.LO — Hand Loose / Sleeve Loose (printed H.LO)
        m.put("HLO",           "H.LO"); m.put("H.LO",         "H.LO");
        m.put("HANDLOOSE",     "H.LO"); m.put("SLEEVELOOSE",  "H.LO");
        m.put("HLOOSE",        "H.LO"); m.put("H.LO.",        "H.LO");

        // AK — Armhole
        m.put("AK",            "AK");   m.put("ARMHOLE",      "AK");
        m.put("ARM HOLE",      "AK");   m.put("ARMH",         "AK");

        // AM — Arm
        m.put("AM",            "AM");   m.put("ARM",          "AM");

        // BN — Back Neck
        m.put("BN",            "BN");   m.put("B.N.",         "BN");
        m.put("BACKNECK",      "BN");   m.put("BACK NECK",    "BN");
        m.put("B.N",           "BN");

        // FN — Front Neck
        m.put("FN",            "FN");   m.put("F.N.",         "FN");
        m.put("FRONTNECK",     "FN");   m.put("FRONT NECK",   "FN");
        m.put("F.N",           "FN");

        // DP-1-1 / DP-1-2 — Dart Point row 1 and 2 (both printed "DP-1" on the form)
        // Position-aware extractor resolves first→DP-1-1, second→DP-1-2
        m.put("DP-1",          "DP-1-1"); m.put("DP1",         "DP-1-1");
        m.put("DP",            "DP-1-1"); m.put("DP-2",        "DP-1-2");
        m.put("DP2",           "DP-1-2"); m.put("DP1.",        "DP-1-1");
        m.put("DP-1.",         "DP-1-1"); m.put("DP1_1",       "DP-1-1");
        m.put("DP1_2",         "DP-1-2"); m.put("DP-1-1",      "DP-1-1");
        m.put("DP-1-2",        "DP-1-2");

        // B-1 — Upper Bust (printed B-1)
        m.put("B1",            "B-1");  m.put("B-1",          "B-1");
        m.put("UPPERBUST",     "B-1");  m.put("UPPER BUST",   "B-1");
        m.put("UB",            "B-1");

        // B-2 — Full Bust (printed B-2)
        m.put("B2",            "B-2");  m.put("B-2",          "B-2");
        m.put("FULLBUST",      "B-2");  m.put("FULL BUST",    "B-2");
        m.put("CHEST",         "B-2");  m.put("BUST",         "B-2");

        // B-3 — Under Bust (printed B-3)
        m.put("B3",            "B-3");  m.put("B-3",          "B-3");
        m.put("UNDERBUST",     "B-3");  m.put("UNDER BUST",   "B-3");
        m.put("WAIST",         "B-3");

        // F-HOOK — Front Hook (printed F.HOOK)
        m.put("F_HOOK",        "F-HOOK"); m.put("F.HOOK",     "F-HOOK");
        m.put("F-HOOK",        "F-HOOK"); m.put("FHOOK",      "F-HOOK");
        m.put("FRONTHOOK",     "F-HOOK"); m.put("F.HOOK.",    "F-HOOK");

        // B-HOOK — Back Hook (printed B.HOOK)
        m.put("B_HOOK",        "B-HOOK"); m.put("B.HOOK",     "B-HOOK");
        m.put("B-HOOK",        "B-HOOK"); m.put("BHOOK",      "B-HOOK");
        m.put("BACKHOOK",      "B-HOOK"); m.put("B.HOOK.",    "B-HOOK");

        // LINING
        m.put("LINING",        "LINING"); m.put("LINER",      "LINING");
        m.put("LNG",           "LINING"); // only for BLOUSE — CHUDI uses LNG for its own "Lining" field

        // AV. — Aari Work (printed AV.)
        m.put("AV",            "AV.");  m.put("AV.",          "AV.");
        m.put("AARI",          "AV.");  m.put("WORK",         "AV.");
        m.put("AARIVORK",      "AV.");  m.put("AW",           "AV.");

        // SARI
        m.put("SARI",          "SARI"); m.put("SAREE",        "SARI");
        m.put("SARE",          "SARI");

        ALIASES = Collections.unmodifiableMap(m);
    }

    // ── Marker patterns — identify BLOUSE slips by measurement labels ─────────

    private static final List<Pattern> MARKER_PATTERNS = List.of(
            Pattern.compile("(?i)\\b(?:F\\.?HOOK|B\\.?HOOK|FHOOK|BHOOK)\\b"),
            Pattern.compile("(?i)\\b(?:DP[\\-\\s]?[12]|DP1)\\b"),
            Pattern.compile("(?i)\\b(?:H\\.?S\\.?|HALF\\s*SHOULDER|HALFSHO)\\b"),
            Pattern.compile("(?i)\\b(?:H\\.?L\\.?|HAND\\s*LENGTH|SLEEVELENGTH)\\b"),
            Pattern.compile("(?i)\\b(?:H\\.?LO|HAND\\s*LOOSE|HLOOSE)\\b"),
            Pattern.compile("(?i)\\b(?:B[\\-\\s]?[123])\\b"),
            Pattern.compile("(?i)\\b(?:BLOUSE\\s*LEN(?:GTH)?)\\b")
    );

    // ── Inline pattern for parsing label + value on same line ────────────────

    private static final Pattern INLINE_PATTERN = Pattern.compile(
            "(?:\\b|[\\(\\[])([A-Za-z][A-Za-z0-9._\\-]{0,10})\\s*[:\\-=|]?\\s*" +
            "([/\\\\|Il1-9][\\d.,]*(?:[.,]\\d+)?|ul|u|ll|ui|ww|w|lo|so|\\d+)(?![/\\-\\d])",
            Pattern.CASE_INSENSITIVE
    );

    // ── MeasurementFormDefinition ─────────────────────────────────────────────

    @Override
    public String garmentType() {
        return "BLOUSE";
    }

    @Override
    public List<String> canonicalKeys() {
        return KEYS;
    }

    @Override
    public Map<String, String> aliases() {
        return ALIASES;
    }

    @Override
    public List<Pattern> markerPatterns() {
        return MARKER_PATTERNS;
    }

    /**
     * Position-aware BLOUSE measurement extractor.
     *
     * <p>Scans lines in order and matches each against {@link #ALIASES}.
     * The first occurrence of a "DP-1" alias resolves to DP1_1;
     * the second occurrence resolves to DP1_2.
     *
     * <p>Supports both:
     * <ul>
     *   <li>In-line:  {@code LTH 14}  (label and value on the same line)</li>
     *   <li>Multi-line: {@code LTH} on one line, {@code 14} on the next</li>
     * </ul>
     */
    @Override
    public Map<String, String> extractMeasurements(String[] lines, List<OcrService.WordBox> wordBoxes) {
        Map<String, String> raw = new LinkedHashMap<>();

        // 1. First attempt: Coordinate-based spatial row matching using wordBoxes
        if (wordBoxes != null && !wordBoxes.isEmpty()) {
            extractSpatialMeasurements(wordBoxes, raw);
        }

        // 2. Fallback / supplementary: Parse text lines to capture any fields not matched spatially
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].strip();
            if (trimmed.isBlank()) continue;

            // Skip header lines
            String upper = trimmed.toUpperCase();
            if (isHeaderLine(upper)) continue;

            // Special check for SARI which may have text notes (e.g. "SARI Green silk" or "SARI Silk")
            Matcher sariMatch = Pattern.compile("(?i)^(?:SARI|SAREE|SARE)\\s*[:\\-=|]?\\s*(.+)$").matcher(trimmed);
            if (sariMatch.find()) {
                String val = sariMatch.group(1).strip();
                if (!val.isBlank()) {
                    raw.putIfAbsent("SARI", val);
                    continue;
                }
            }

            // In-line: label and value on the same line
            Matcher m = INLINE_PATTERN.matcher(trimmed);
            boolean matchedInline = false;

            while (m.find()) {
                String rawLabel = m.group(1).strip().toUpperCase()
                        .replaceAll("^[\\[(]+", "")
                        .replaceAll("[,:\\-=|]+$", "");
                String val = cleanValue(m.group(2));
                if (val == null || val.length() > 4) continue;

                String key = resolveKey(rawLabel, raw);
                if (key != null) {
                    raw.putIfAbsent(key, val);
                    matchedInline = true;
                }
            }

            // Multi-line: label only on this line, value on next non-empty line
            if (!matchedInline) {
                String labelCandidate = upper
                        .replaceAll("^[\\[(]+", "")
                        .replaceAll("[^A-Z0-9._\\-]", "");
                String key = resolveKey(labelCandidate, raw);
                if (key != null) {
                    if ("SARI".equals(key)) {
                        int nextIdx = i + 1;
                        while (nextIdx < lines.length && lines[nextIdx].strip().isBlank()) nextIdx++;
                        if (nextIdx < lines.length) {
                            String nextLine = lines[nextIdx].strip();
                            if (!isHeaderLine(nextLine.toUpperCase())) {
                                raw.putIfAbsent("SARI", nextLine);
                            }
                        }
                        continue;
                    }
                    int nextIdx = i + 1;
                    while (nextIdx < lines.length && lines[nextIdx].strip().isBlank()) nextIdx++;
                    if (nextIdx < lines.length) {
                        Matcher numMatch = Pattern.compile(
                                "^([/\\\\|Il1-9][\\d.,]*|ul|ll|ui|ww|w|lo|so|\\d+)"
                        ).matcher(lines[nextIdx].strip());
                        if (numMatch.find()) {
                            String val = cleanValue(numMatch.group(1));
                            if (val != null && val.length() <= 4) {
                                raw.putIfAbsent(key, val);
                            }
                        }
                    }
                }
            }
        }

        // Return in canonical key order
        Map<String, String> result = new LinkedHashMap<>();
        for (String k : KEYS) {
            String v = raw.get(k);
            if (v != null) result.put(k, v);
        }

        log.debug("BLOUSE extractor: matched {}/{} template fields", result.size(), KEYS.size());
        return result;
    }

    private record DetectedLabel(String key, double x, double y, double w, double h) {}

    private void extractSpatialMeasurements(List<OcrService.WordBox> wordBoxes, Map<String, String> raw) {
        // Filter boxes in MEASUREMENT_LEFT zone (left side of page, below header)
        List<OcrService.WordBox> measZone = wordBoxes.stream()
                .filter(b -> b.x() <= 0.62 && b.y() >= 0.15 && b.y() <= 0.98)
                .sorted(Comparator.comparingDouble((OcrService.WordBox b) -> b.y()).thenComparingDouble((OcrService.WordBox b) -> b.x()))
                .toList();

        if (measZone.isEmpty()) return;

        List<DetectedLabel> labels = new ArrayList<>();
        for (int i = 0; i < measZone.size(); i++) {
            OcrService.WordBox b1 = measZone.get(i);
            String t1 = b1.text().toUpperCase().replaceAll("^[\\[(]+", "").replaceAll("[.:,\\-=|]+$", "");
            if (isHeaderLine(t1)) continue;

            String key = null;
            double lx = b1.x(), ly = b1.y(), lw = b1.width(), lh = b1.height();

            // Try 2-word combined label first (e.g. "DP" "1", "H." "S.", "F." "HOOK", "B." "1")
            if (i + 1 < measZone.size()) {
                OcrService.WordBox b2 = measZone.get(i + 1);
                if (Math.abs(b2.y() - b1.y()) < 0.025 && b2.x() > b1.x() && (b2.x() - (b1.x() + b1.width())) < 0.08) {
                    String combined = (t1 + b2.text().toUpperCase().replaceAll("[.:,\\-=|]+$", "")).replaceAll("[\\s.\\-_]+", "");
                    if (ALIASES.containsKey(combined)) {
                        key = ALIASES.get(combined);
                        lw = (b2.x() + b2.width()) - lx;
                        lh = Math.max(lh, b2.height());
                        i++; // consume b2
                    }
                }
            }

            // Try single-word label
            if (key == null) {
                String clean = t1.replaceAll("[\\s.\\-_]+", "");
                if (ALIASES.containsKey(t1)) {
                    key = ALIASES.get(t1);
                } else if (ALIASES.containsKey(clean)) {
                    key = ALIASES.get(clean);
                }
            }

            if (key != null) {
                labels.add(new DetectedLabel(key, lx, ly, lw, lh));
            }
        }

        // Sort labels top-to-bottom and resolve DP-1-1 vs DP-1-2 ordinally
        labels.sort(Comparator.comparingDouble((DetectedLabel dl) -> dl.y()));
        List<DetectedLabel> resolvedLabels = new ArrayList<>();
        boolean seenDp1 = false;
        for (DetectedLabel dl : labels) {
            String k = dl.key();
            if ("DP-1-1".equals(k)) {
                if (!seenDp1) {
                    seenDp1 = true;
                } else {
                    k = "DP-1-2";
                }
            }
            resolvedLabels.add(new DetectedLabel(k, dl.x(), dl.y(), dl.w(), dl.h()));
        }

        // For each label, find the closest numeric value on the same row to the right
        for (DetectedLabel dl : resolvedLabels) {
            String key = dl.key();
            if (raw.containsKey(key)) continue;

            OcrService.WordBox bestValBox = null;
            double minXDist = Double.MAX_VALUE;

            for (OcrService.WordBox cand : measZone) {
                if (cand.x() < dl.x() + dl.w() * 0.40) continue;
                if (cand.x() > 0.65) continue;

                double labelMidY = dl.y() + dl.h() / 2.0;
                double candMidY = cand.y() + cand.height() / 2.0;
                double rowTol = Math.max(dl.h(), 0.038) * 1.15;
                if (Math.abs(labelMidY - candMidY) > rowTol) continue;

                String cleaned = cleanValue(cand.text());
                if (cleaned == null && "SARI".equals(key) && !cand.text().isBlank()) {
                    cleaned = cand.text().strip();
                }

                if (cleaned != null && cleaned.length() <= 6) {
                    double xDist = cand.x() - (dl.x() + dl.w());
                    if (xDist >= -0.01 && xDist < minXDist) {
                        minXDist = xDist;
                        bestValBox = cand;
                    }
                }
            }

            if (bestValBox != null) {
                String val = cleanValue(bestValBox.text());
                if (val == null && "SARI".equals(key)) val = bestValBox.text().strip();
                if (val != null) {
                    raw.put(key, val);
                }
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    public String resolveKey(String rawLabel) {
        return resolveKey(rawLabel, Collections.emptyMap());
    }

    public String resolveKey(String rawLabel, Map<String, String> raw) {
        // Direct alias lookup
        String key = ALIASES.get(rawLabel);
        if (key == null) {
            // Strip punctuation and retry
            key = ALIASES.get(rawLabel.replaceAll("[.\\s]+", ""));
        }
        if (key == null) {
            // Dash-normalised retry
            key = ALIASES.get(rawLabel.replaceAll("[.\\-\\s]+", ""));
        }
        if (key != null) {
            // Position-aware duplicate resolution for DP-1-1 / DP-1-2
            if ("DP-1-1".equals(key) && raw.containsKey("DP-1-1")) {
                return "DP-1-2";
            }
        }
        return key;
    }

    private boolean isHeaderLine(String upper) {
        return upper.matches("^(?:NAME|CUSTOMER|CUST|ERODE|DATE|DUE\\s*DATE|" +
                "PH(?:ONE)?|MOBILE|TOTAL|ADVANCE|PAID|BALANCE|CLOTH|" +
                "BLOUSE|RITHAM|OR\\.?\\s*DATE)\\b.*");
    }

    private String cleanValue(String raw) {
        if (raw == null) return null;
        String val = raw.strip();
        // OCR substitution corrections for numeric-only measurement fields
        if (val.equalsIgnoreCase("ul") || val.equalsIgnoreCase("u") ||
                val.equalsIgnoreCase("ll") || val.equalsIgnoreCase("ui")) return "11";
        if (val.equalsIgnoreCase("ww") || val.equalsIgnoreCase("w"))     return "11";
        if (val.equalsIgnoreCase("lo") || val.equalsIgnoreCase("so"))    return "10";
        if (val.startsWith("|") || val.startsWith("(") || val.startsWith("[")) {
            val = val.substring(1).strip();
        }
        if (val.matches("^[/\\\\|Il]\\d+")) {
            val = "1" + val.substring(1);
        }
        val = val.replaceAll("[^0-9.]+$", "");
        return val.isBlank() ? null : val;
    }
}
