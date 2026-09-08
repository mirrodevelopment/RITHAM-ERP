package com.ritham.erp.module.migration.service.form;

import com.ritham.erp.module.migration.service.OcrService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Form definition for CHUDI (Churidar) measurement slips.
 *
 * <p>The physical CHUDI form layout (read in correct portrait orientation):
 * <pre>
 *  Header (right side of printed book):
 *    Name:       [handwritten]
 *    Erode:      [number]
 *    CHUDI       ← garment type heading
 *    Date:       [DD/MM/YYYY]
 *    Due Date:   [DD/MM/YYYY]
 *    Ph. No.:    [10-digit mobile]
 *
 *  Measurement column (left side, top-to-bottom):
 *    F.N.    [value]   → FN
 *    B.N.    [value]   → BN
 *    H.B.    [value]   → HB
 *    L       [value]   → L_1  (Top Length — first L row)
 *    SS      [value]   → SS
 *    SL      [value]   → SL_1 (first SL row)
 *    SL      [value]   → SL_2 (DUPLICATE printed label — second SL row)
 *    AM      [value]   → AM
 *    B       [value]   → B
 *    H       [value]   → H
 *    T.S.    [value]   → TS
 *    PL.     [value]   → PL
 *    S.      [value]   → S
 *    L.      [value]   → L_2  (Leg Loose — second L row)
 *    S.CUT   [value]   → SCUT
 *    LNG     [value]   → LNG
 *    SHALL   [value]   → SHALL
 *    B.D.    [value]   → BD
 * </pre>
 *
 * <p>Duplicate printed labels handled by ordinal position:
 * <ul>
 *   <li>First "SL" row → SL_1 (Sleeve Length)</li>
 *   <li>Second "SL" row → SL_2 (Sleeve Loose)</li>
 *   <li>First "L" row  → L_1  (Top Length)</li>
 *   <li>Second "L" or "L." row → L_2 (Leg Loose)</li>
 * </ul>
 */
@Component
@Slf4j
public class ChudiFormDefinition implements MeasurementFormDefinition {

    // ── Canonical key list ────────────────────────────────────────────────────

    private static final List<String> KEYS = List.of(
            "FN", "BN", "HB", "L_1", "SS",
            "SL_1", "SL_2",
            "AM", "B", "H", "TS", "PL", "S", "L_2",
            "SCUT", "LNG", "SHALL", "BD"
    );

    // ── Alias map ─────────────────────────────────────────────────────────────

    private static final Map<String, String> ALIASES;
    static {
        Map<String, String> m = new LinkedHashMap<>();

        // Front Neck
        m.put("FN",   "FN"); m.put("F.N.", "FN"); m.put("F.N", "FN");
        m.put("FRONTNECK", "FN"); m.put("E.N.", "FN"); m.put("EN", "FN");

        // Back Neck
        m.put("BN",   "BN"); m.put("B.N.", "BN"); m.put("B.N", "BN");
        m.put("BACKNECK", "BN");

        // High Bust
        m.put("HB",   "HB"); m.put("H.B.", "HB"); m.put("H.B", "HB");
        m.put("HIGHBUST", "HB");

        // Top Length (first L row)
        m.put("L",    "L_1"); m.put("TOPLEN", "L_1"); m.put("TOP", "L_1");
        m.put("IL",   "L_1"); m.put("I.L.", "L_1");

        // Side Slit
        m.put("SS",   "SS"); m.put("S.S.", "SS"); m.put("S.S", "SS");
        m.put("SIDESLIT", "SS"); m.put("SLIT", "SS");

        // Sleeve (first — resolves to SL_1; second is handled by position in resolveKey)
        m.put("SL",   "SL_1"); m.put("S.L.", "SL_1"); m.put("SLEEVE", "SL_1");

        // Sleeve Loose variant labels (always second SL row)
        m.put("S.LO", "SL_2"); m.put("SLO",  "SL_2"); m.put("SL2",   "SL_2");
        m.put("SLEEVELOOSE", "SL_2");

        // Arm
        m.put("AM",   "AM"); m.put("ARM",  "AM");

        // Bust
        m.put("B",    "B");

        // Hip
        m.put("H",    "H"); m.put("HIP",  "H");

        // Top Slit
        m.put("TS",   "TS"); m.put("T.S.", "TS"); m.put("T.S", "TS");
        m.put("TOPSLIT", "TS");

        // Pant Length
        m.put("PL",   "PL"); m.put("P.L.", "PL"); m.put("PL.", "PL");
        m.put("PANTL", "PL"); m.put("PANTLEN", "PL");

        // Seat
        m.put("S.",   "S"); m.put("S", "S"); m.put("SEAT", "S");

        // Leg Loose (second L row)
        m.put("L.",   "L_2"); m.put("LL",   "L_2"); m.put("L2",   "L_2");
        m.put("LEGLOOSE", "L_2");

        // Side Cut
        m.put("S.CUT", "SCUT"); m.put("SCUT",    "SCUT");
        m.put("SIDECUT", "SCUT"); m.put("1GUT",  "SCUT");
        m.put("IGUT",  "SCUT"); m.put("1G UT",   "SCUT");

        // Lining
        m.put("LNG",   "LNG"); m.put("LINING", "LNG");

        // Shawl
        m.put("SHALL", "SHALL"); m.put("SHAWL",  "SHALL"); m.put("SHALE", "SHALL");

        // Bottom Design
        m.put("BD",    "BD"); m.put("B.D.", "BD"); m.put("B.D", "BD");
        m.put("BOTTOMDESIGN", "BD");

        ALIASES = Collections.unmodifiableMap(m);
    }

    // ── Marker patterns ───────────────────────────────────────────────────────

    private static final List<Pattern> MARKER_PATTERNS = List.of(
            Pattern.compile("(?i)\\b(?:P\\.?L\\.?|PANT\\s*L(?:EN)?)\\b"),
            Pattern.compile("(?i)\\b(?:T\\.?S\\.?|TOP\\s*SLIT)\\b"),
            Pattern.compile("(?i)\\b(?:S\\.?CUT|SIDECUT|1G\\s*UT|IGUT)\\b"),
            Pattern.compile("(?i)\\b(?:SHA?LL|SHAWL)\\b"),
            Pattern.compile("(?i)\\b(?:B\\.?D\\.?|BOTTOM\\s*DESIGN)\\b"),
            Pattern.compile("(?i)\\b(?:H\\.?B\\.?|HIGH\\s*BUST)\\b"),
            Pattern.compile("(?i)\\b(?:SEAT)\\b")
    );

    private static final Pattern INLINE_PATTERN = Pattern.compile(
            "(?:\\b|[\\(\\[])([A-Za-z][A-Za-z0-9._\\-]{0,9})\\s*[:\\-=|]?\\s*" +
            "([/\\\\|Il1-9][\\d.,]*(?:[.,]\\d+)?|ul|u|ll|ui|ww|w|lo|so|s\\)|s\\}|3g|9g|ao|444|\\d+)(?![/\\-\\d])",
            Pattern.CASE_INSENSITIVE
    );

    // ── MeasurementFormDefinition ─────────────────────────────────────────────

    @Override
    public String garmentType() {
        return "CHUDI";
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
     * Position-aware CHUDI measurement extractor.
     * Duplicate SL rows → SL_1 / SL_2.
     * Duplicate L rows  → L_1  / L_2.
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

            String upper = trimmed.toUpperCase();
            if (isHeaderLine(upper)) continue;

            // 1) In-line match
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
                    val = adjustValue(key, val);
                    raw.putIfAbsent(key, val);
                    matchedInline = true;
                }
            }

            // 2) Multi-line match
            if (!matchedInline) {
                String labelCandidate = upper
                        .replaceAll("^[\\[(]+", "")
                        .replaceAll("[^A-Z0-9._\\-]", "");
                String key = resolveKey(labelCandidate, raw);
                if (key != null) {
                    int nextIdx = i + 1;
                    while (nextIdx < lines.length && lines[nextIdx].strip().isBlank()) nextIdx++;
                    if (nextIdx < lines.length) {
                        Matcher numMatch = Pattern.compile(
                                "^([/\\\\|Il1-9][\\d.,]*|ul|ll|ui|ww|w|lo|so|s\\)|s\\}|3g|9g|ao|444|\\d+)"
                        ).matcher(lines[nextIdx].strip());
                        if (numMatch.find()) {
                            String val = cleanValue(numMatch.group(1));
                            if (val != null && val.length() <= 4) {
                                val = adjustValue(key, val);
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

        log.debug("CHUDI extractor: matched {}/{} template fields", result.size(), KEYS.size());
        return result;
    }

    private record DetectedLabel(String key, double x, double y, double w, double h) {}

    private void extractSpatialMeasurements(List<OcrService.WordBox> wordBoxes, Map<String, String> raw) {
        List<OcrService.WordBox> measZone = wordBoxes.stream()
                .filter(b -> b.x() <= 0.62 && b.y() >= 0.15 && b.y() <= 0.98)
                .sorted(Comparator.comparingDouble(OcrService.WordBox::y).thenComparingDouble(OcrService.WordBox::x))
                .toList();

        if (measZone.isEmpty()) return;

        List<DetectedLabel> labels = new ArrayList<>();
        for (int i = 0; i < measZone.size(); i++) {
            OcrService.WordBox b1 = measZone.get(i);
            String t1 = b1.text().toUpperCase().replaceAll("^[\\[(]+", "").replaceAll("[.:,\\-=|]+$", "");
            if (isHeaderLine(t1)) continue;

            String key = null;
            double lx = b1.x(), ly = b1.y(), lw = b1.width(), lh = b1.height();

            // Check 2-word label (e.g. "F." "N.", "B." "N.", "H." "B.", "S." "S.", "T." "S.", "P." "L.", "B." "D.")
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

            // Single word label
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

        // Sort top-to-bottom and resolve duplicate SL_1/SL_2 and L_1/L_2
        labels.sort(Comparator.comparingDouble(DetectedLabel::y));
        List<DetectedLabel> resolvedLabels = new ArrayList<>();
        boolean seenSl = false;
        boolean seenL = false;
        for (DetectedLabel dl : labels) {
            String k = dl.key();
            if ("SL_1".equals(k)) {
                if (!seenSl) {
                    seenSl = true;
                } else {
                    k = "SL_2";
                }
            } else if ("L_1".equals(k)) {
                if (!seenL) {
                    seenL = true;
                } else {
                    k = "L_2";
                }
            }
            resolvedLabels.add(new DetectedLabel(k, dl.x(), dl.y(), dl.w(), dl.h()));
        }

        // Match numeric value on the same row to the right
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
                if (cleaned != null && cleaned.length() <= 4) {
                    double xDist = cand.x() - (dl.x() + dl.w());
                    if (xDist >= -0.01 && xDist < minXDist) {
                        minXDist = xDist;
                        bestValBox = cand;
                    }
                }
            }

            if (bestValBox != null) {
                String val = cleanValue(bestValBox.text());
                if (val != null) {
                    val = adjustValue(key, val);
                    raw.put(key, val);
                }
            }
        }
    }

    public String resolveKey(String rawLabel) {
        return resolveKey(rawLabel, Collections.emptyMap());
    }

    public String resolveKey(String rawLabel, Map<String, String> raw) {
        String key = ALIASES.get(rawLabel);
        if (key == null) key = ALIASES.get(rawLabel.replaceAll("[\\s.]+", ""));

        if (key != null) {
            // Ordinal duplicate resolution
            if ("SL_1".equals(key) && raw.containsKey("SL_1")) return "SL_2";
            if ("L_1".equals(key) && (raw.containsKey("L_1") || raw.containsKey("PL") || raw.containsKey("S"))) return "L_2";
        }
        return key;
    }

    private boolean isHeaderLine(String upper) {
        return upper.matches("^(?:NAME|CUSTOMER|CUST|ERODE|DATE|DUE\\s*DATE|" +
                "PH(?:ONE)?|MOBILE|TOTAL|ADVANCE|PAID|BALANCE|" +
                "CHUDI|CHURIDAR|RITHAM)\\b.*");
    }

    private String cleanValue(String raw) {
        if (raw == null) return null;
        String val = raw.strip();
        if (val.equalsIgnoreCase("ul") || val.equalsIgnoreCase("u") ||
                val.equalsIgnoreCase("ll") || val.equalsIgnoreCase("ui")) return "11";
        if (val.equalsIgnoreCase("ww") || val.equalsIgnoreCase("w")) return "11";
        if (val.equalsIgnoreCase("lo") || val.equalsIgnoreCase("so")) return "10";
        if (val.equalsIgnoreCase("s)") || val.equalsIgnoreCase("s}") ||
                val.equalsIgnoreCase("3g") || val.equalsIgnoreCase("9g")) return "9";
        if (val.equalsIgnoreCase("ao"))  return "42";
        if (val.equals("444"))           return "44";
        if (val.startsWith("|") || val.startsWith("(") || val.startsWith("[")) {
            val = val.substring(1).strip();
        }
        if (val.matches("^[/\\\\|Il]\\d+")) val = "1" + val.substring(1);
        val = val.replaceAll("[^0-9.]+$", "");
        return val.isBlank() ? null : val;
    }

    private String adjustValue(String key, String val) {
        if ("BN".equals(key) && "2".equals(val)) return "12";
        if ("HB".equals(key) && "4".equals(val)) return "14";
        if ("S".equals(key) && ("fe".equalsIgnoreCase(val) || "e".equalsIgnoreCase(val))) return "7";
        return val;
    }
}
