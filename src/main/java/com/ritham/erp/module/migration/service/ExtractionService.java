package com.ritham.erp.module.migration.service;

import tools.jackson.databind.ObjectMapper;
import com.ritham.erp.module.migration.dto.ExtractedData;
import com.ritham.erp.module.migration.dto.ExtractedData.ExtractedCustomerData;
import com.ritham.erp.module.migration.dto.ExtractedData.ExtractedOrderData;
import com.ritham.erp.module.migration.service.form.FormDefinitionRegistry;
import com.ritham.erp.module.migration.service.form.MeasurementFormDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts raw OCR text into a structured {@link ExtractedData} DTO.
 *
 * <p>Each field is extracted independently; partial extraction is acceptable.
 * Every field carries a confidence score.  Fields below the configured threshold
 * are flagged {@code needsReview = true} so the Review Desk highlights them.
 *
 * <p>Extraction rules are regex + keyword heuristics calibrated for typical
 * South Indian tailoring order paper formats.
 */
@Service
@Slf4j
public class ExtractionService {

    /** Threshold below which a field is flagged for human review (0.0 – 1.0). */
    @Value("${app.migration.ocr-confidence-threshold:0.85}")
    private double reviewThreshold;

    private final ObjectMapper objectMapper;
    private final FormDefinitionRegistry formRegistry;

    @org.springframework.beans.factory.annotation.Autowired
    public ExtractionService(ObjectMapper objectMapper, FormDefinitionRegistry formRegistry) {
        this.objectMapper = objectMapper;
        this.formRegistry = formRegistry;
    }

    public ExtractionService(ObjectMapper objectMapper) {
        this(objectMapper, new FormDefinitionRegistry(java.util.List.of(
                new com.ritham.erp.module.migration.service.form.BlouseFormDefinition(),
                new com.ritham.erp.module.migration.service.form.ChudiFormDefinition()
        )));
    }

    public ExtractionService() {
        this(new ObjectMapper());
    }

    // ── Compiled patterns ─────────────────────────────────────────────────────

    private static final Pattern MOBILE_PATTERN =
            Pattern.compile("(?<![\\d])([6-9]\\d{9})(?![\\d])");

    private static final Pattern DATE_PATTERN =
            Pattern.compile("(\\d{1,2})[/\\-\\.\\|\\]\\[\\}\\)\\s]+(\\d{1,2})[/\\-\\.\\|\\]\\[\\}\\)\\s]+(\\d{2,4})");

    private static final Pattern AMOUNT_PATTERN =
            Pattern.compile("(?:Rs\\.?|₹|INR)?\\s*(\\d+(?:[.,]\\d{1,2})?)");

    /**
     * Matches abbreviated measurement field labels produced by OCR.
     * Key may contain uppercase letters, digits, dots and hyphens (e.g. F.N., DP-1, H.L.).
     */
    private static final Pattern MEASUREMENT_PATTERN =
            Pattern.compile("([A-Z][A-Z0-9.\\-]{0,7})\\s*[:\\-]?\\s*(\\d+(?:[.,]\\d+)?)");

    // ── Garment keyword detection (regex handles OCR char-swap variants) ─────

    /**
     * Ordered list of (pattern, canonical-garment) pairs.
     * Checked in order so more specific patterns come first.
     */
    private static final List<Map.Entry<Pattern, String>> GARMENT_PATTERNS = List.of(
            // CHUDI / CHURIDAR first — OCR frequently swaps I↔1 or l
            Map.entry(Pattern.compile("CHU[RR]?[IL1l][DdD][AI1l][AR]?", Pattern.CASE_INSENSITIVE), "CHURIDAR"),
            Map.entry(Pattern.compile("CHU[DdD][I1l]",                    Pattern.CASE_INSENSITIVE), "CHUDI"),
            Map.entry(Pattern.compile("BLOUSE",                            Pattern.CASE_INSENSITIVE), "BLOUSE"),
            Map.entry(Pattern.compile("SAREE|SARI",                        Pattern.CASE_INSENSITIVE), "SAREE"),
            Map.entry(Pattern.compile("SALWAR",                            Pattern.CASE_INSENSITIVE), "SALWAR"),
            Map.entry(Pattern.compile("LEHENGA",                           Pattern.CASE_INSENSITIVE), "LEHENGA"),
            Map.entry(Pattern.compile("KURTI",                             Pattern.CASE_INSENSITIVE), "KURTI"),
            Map.entry(Pattern.compile("PAVADAI",                           Pattern.CASE_INSENSITIVE), "PAVADAI"),
            Map.entry(Pattern.compile("SKIRT",                             Pattern.CASE_INSENSITIVE), "SKIRT"),
            Map.entry(Pattern.compile("PANT",                              Pattern.CASE_INSENSITIVE), "PANT"),
            Map.entry(Pattern.compile("SHIRT",                             Pattern.CASE_INSENSITIVE), "SHIRT")
    );

    // ── Garment-specific canonical key lists ───────────────────────────────

    /**
     * Canonical key lists — delegated to form definitions.
     * Kept as convenience methods for backwards compatibility in calculateCompletionRatio.
     * Extraction is handled by registered FormDefinitions via formRegistry.
     */
    private List<String> getChudiKeys() {
        var opt = formRegistry.find("CHUDI");
        if (opt.isPresent()) {
            return opt.get().canonicalKeys();
        }
        return List.of(
                "FN", "BN", "HB", "L_1", "SS", "SL_1", "SL_2",
                "AM", "B", "H", "TS", "PL", "S", "L_2",
                "SCUT", "LNG", "SHALL", "BD"
        );
    }

    private List<String> getBlouseKeys() {
        var opt = formRegistry.find("BLOUSE");
        if (opt.isPresent()) {
            return opt.get().canonicalKeys();
        }
        return List.of(
                "LTH", "SHO", "HS", "HL", "HLO", "AK", "AM", "BN", "FN",
                "DP1_1", "DP1_2", "B1", "B2", "B3", "F_HOOK", "B_HOOK",
                "LINING", "AV", "SARI"
        );
    }

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("d.M.yyyy"),
            DateTimeFormatter.ofPattern("d/M/yy"),
            DateTimeFormatter.ofPattern("d-M-yy")
    );

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Extract structured data from raw OCR text using default portrait orientation.
     *
     * @param rawText the text output from Tesseract
     * @return ExtractedData with confidence scores; never null
     */
    public ExtractedData extract(String rawText) {
        return extract(rawText, Collections.emptyList(), "PORTRAIT");
    }

    /**
     * Extract structured data from raw OCR text with orientation and dynamic text-anchored bounding boxes.
     *
     * @param rawText the text output from Tesseract
     * @param wordBoxes recognized words with normalized bounding coordinates
     * @param orientation detected orientation: PORTRAIT or LANDSCAPE
     * @return ExtractedData with dynamic regions; never null
     */
    public ExtractedData extract(String rawText, List<OcrService.WordBox> wordBoxes, String orientation) {
        if (rawText == null || rawText.isBlank()) {
            log.warn("Empty OCR text supplied to extraction service");
            return emptyResult();
        }

        String[] lines = rawText.split("\\r?\\n");

        // ── Customer ──────────────────────────────────────────────────────────
        String mobile = extractMobile(rawText, wordBoxes);
        double mobileConf = mobile != null ? 0.97 : 0.0;

        String name = extractName(lines, rawText, wordBoxes);
        double nameConf = name != null ? 0.85 : 0.0;

        // ── Dates ─────────────────────────────────────────────────────────────
        List<String> dates = extractAllDates(rawText);
        String orderDate    = extractOrderDate(rawText, dates);
        String deliveryDate = extractDeliveryDate(rawText, dates, orderDate);
        double orderDateConf    = orderDate != null ? 0.82 : 0.0;
        double deliveryDateConf = deliveryDate != null ? 0.80 : 0.0;

        // ── Order ID (Header field, physical label often Erode : or Order ID :) ─────
        String orderId = extractOrderId(rawText, wordBoxes);

        // ── Garment ───────────────────────────────────────────────────────────
        // Strictly detected from the TOP-CENTER / header area.
        // DO NOT infer garment type from measurement names (as per spec).
        String garment = extractTopCenterGarmentType(lines, wordBoxes);
        double garmentConf;
        if (garment == null) {
            garment = "NEEDS_REVIEW";
            garmentConf = 0.0;
        } else {
            garmentConf = 0.95;
        }

        // ── Measurements ──────────────────────────────────────────────────────
        // Delegate to the appropriate form definition's position-aware extractor.
        // Both BLOUSE and CHUDI use dedicated extractors that preserve duplicate rows.
        Map<String, String> measurements;
        Optional<MeasurementFormDefinition> formDef = formRegistry.find(garment);
        if (formDef.isPresent()) {
            measurements = formDef.get().extractMeasurements(lines, wordBoxes);
        } else if ("NEEDS_REVIEW".equals(garment)) {
            // Cannot identify form — run both and take whichever yields more fields
            Map<String, String> blouseResult = formRegistry.find("BLOUSE")
                    .map(d -> d.extractMeasurements(lines, wordBoxes))
                    .orElse(Collections.emptyMap());
            Map<String, String> chudiResult = formRegistry.find("CHUDI")
                    .map(d -> d.extractMeasurements(lines, wordBoxes))
                    .orElse(Collections.emptyMap());
            measurements = blouseResult.size() >= chudiResult.size() ? blouseResult : chudiResult;
        } else {
            Map<String, String> rawMeasurements = extractMeasurements(rawText);
            measurements = normalizeMeasurements(garment, rawMeasurements);
        }
        Map<String, Double>  measConfidences   = buildMeasurementConfidences(measurements);

        // ── Amounts ───────────────────────────────────────────────────────────
        BigDecimal total   = extractAmount(rawText, "total", "amount", "cost");
        BigDecimal advance = extractAmount(rawText, "advance", "adv", "paid");
        double totalConf   = total   != null ? 0.85 : 0.0;
        double advConf     = advance != null ? 0.80 : 0.0;
        boolean paymentDateKnown = containsExplicitDate(rawText, "paid", "receipt", "payment");

        // ── Review flags ──────────────────────────────────────────────────────
        boolean customerNeedsReview = mobileConf < reviewThreshold || nameConf < reviewThreshold;
        // NEEDS_REVIEW garment type always forces review on both sides
        boolean garmentNeedsReview = "NEEDS_REVIEW".equals(garment) || garmentConf < reviewThreshold;
        boolean orderNeedsReview    = garmentNeedsReview
                || (total != null && totalConf < reviewThreshold)
                || measurements.isEmpty();
        if (garmentNeedsReview) customerNeedsReview = true;

        ExtractedCustomerData customer = new ExtractedCustomerData(
                name, nameConf, mobile, mobileConf, customerNeedsReview);

        ExtractedOrderData order = new ExtractedOrderData(
                orderDate, orderDateConf,
                deliveryDate, deliveryDateConf,
                garment, garmentConf,
                "WITHOUT_LINING",
                measurements, measConfidences,
                total, totalConf,
                advance, advConf,
                paymentDateKnown,
                orderNeedsReview,
                orderId
        );

        double overall = calculateCompletionRatio(new ExtractedData(customer, order, 0.0));

        // ── Dynamic Annotation Regions (Text-Anchored) ───────────────────────
        Map<String, ExtractedData.RegionBox> dynamicRegions = computeDynamicRegions(
                wordBoxes, garment, measurements, name, mobile, orderDate, deliveryDate, orderId);

        return new ExtractedData(customer, order, overall, dynamicRegions, orientation != null ? orientation : "PORTRAIT");
    }

    private Map<String, ExtractedData.RegionBox> computeDynamicRegions(
            List<OcrService.WordBox> wordBoxes,
            String garmentType,
            Map<String, String> measurements,
            String customerName,
            String customerMobile,
            String orderDate,
            String deliveryDate,
            String orderId
    ) {
        Map<String, ExtractedData.RegionBox> regions = new LinkedHashMap<>();

        final String COLOR_BLUE = "#2563eb";
        final String COLOR_GREEN = "#16a34a";
        final String COLOR_PURPLE = "#9333ea";
        final String COLOR_RED = "#dc2626";
        final String COLOR_ORANGE = "#ea580c";

        if (wordBoxes != null && !wordBoxes.isEmpty()) {
            // ── 1. Customer Name (BLUE #2563eb) & Order ID (GREEN #16a34a) ───────
            List<OcrService.WordBox> custNameBoxes = new ArrayList<>();
            Set<String> nameWords = new HashSet<>();
            if (customerName != null) {
                for (String part : customerName.toUpperCase().split("\\s+")) {
                    if (part.length() > 1) nameWords.add(part);
                }
            }
            nameWords.add("NAME");
            nameWords.add("CUSTOMER");

            for (OcrService.WordBox wb : wordBoxes) {
                if (wb.y() <= 0.35 && wb.x() <= 0.60) {
                    String u = wb.text().toUpperCase();
                    for (String nw : nameWords) {
                        if (u.contains(nw) || nw.contains(u)) {
                            custNameBoxes.add(wb);
                            break;
                        }
                    }
                }
            }

            ExtractedData.RegionBox fieldCustName = createBoundingBox(custNameBoxes, 0.02, 0.01,
                    COLOR_BLUE, "Customer Name", List.of("revCustomerName"));
            if (fieldCustName != null) {
                regions.put("field_revCustomerName", fieldCustName);
            }

            List<OcrService.WordBox> orderIdBoxes = new ArrayList<>();
            for (OcrService.WordBox wb : wordBoxes) {
                if (wb.y() <= 0.35 && wb.x() <= 0.60) {
                    String u = wb.text().toUpperCase();
                    if (u.contains("ORDER") || u.contains("ERODE") || (orderId != null && !orderId.isBlank() && wb.text().contains(orderId))) {
                        orderIdBoxes.add(wb);
                    }
                }
            }
            ExtractedData.RegionBox fieldOrderId = createBoundingBox(orderIdBoxes, 0.02, 0.01,
                    COLOR_GREEN, "Order ID", List.of("revOrderId"));
            if (fieldOrderId != null) {
                regions.put("field_revOrderId", fieldOrderId);
            }

            List<OcrService.WordBox> allCustBoxes = new ArrayList<>();
            allCustBoxes.addAll(custNameBoxes);
            allCustBoxes.addAll(orderIdBoxes);
            ExtractedData.RegionBox custRegion = createBoundingBox(allCustBoxes, 0.025, 0.015,
                    COLOR_BLUE, "Customer Info (Name / Order ID)", List.of("revCustomerName", "revOrderId"));
            if (custRegion != null) {
                regions.put("customerRegion", custRegion);
            }

            // ── 2. Garment Type (PURPLE #9333ea) ──────────────────────────────────
            List<OcrService.WordBox> garmBoxes = new ArrayList<>();
            String gKey = (garmentType != null && !"NEEDS_REVIEW".equals(garmentType)) ? garmentType.toUpperCase() : "CHUDI";
            for (OcrService.WordBox wb : wordBoxes) {
                if (wb.y() <= 0.35) {
                    String u = wb.text().toUpperCase();
                    if (u.contains("CHUDI") || u.contains("BLOUSE") || u.contains("CHURIDAR") || u.contains(gKey)) {
                        garmBoxes.add(wb);
                    }
                }
            }
            ExtractedData.RegionBox fieldGarment = createBoundingBox(garmBoxes, 0.02, 0.01,
                    COLOR_PURPLE, "Garment Type", List.of("revGarmentType"));
            if (fieldGarment != null) {
                if (fieldGarment.h() > 0.18) {
                    fieldGarment = new ExtractedData.RegionBox(
                            fieldGarment.x(), fieldGarment.y(), fieldGarment.w(), 0.08,
                            fieldGarment.color(), fieldGarment.label(), fieldGarment.fields()
                    );
                }
                regions.put("field_revGarmentType", fieldGarment);
                regions.put("garmentRegion", fieldGarment);
            }

            // ── 3. Phone & Dates (RED #dc2626) ───────────────────────────────────
            List<OcrService.WordBox> mobileBoxes = new ArrayList<>();
            for (OcrService.WordBox wb : wordBoxes) {
                if (wb.y() <= 0.35) {
                    String u = wb.text().toUpperCase();
                    if (u.contains("PH") || u.contains("MOB") ||
                            (customerMobile != null && !customerMobile.isBlank() && wb.text().replaceAll("[^\\d]", "").contains(customerMobile))) {
                        mobileBoxes.add(wb);
                    }
                }
            }
            ExtractedData.RegionBox fieldMobile = createBoundingBox(mobileBoxes, 0.02, 0.01,
                    COLOR_RED, "Mobile", List.of("revCustomerMobile"));
            if (fieldMobile != null) {
                regions.put("field_revCustomerMobile", fieldMobile);
            }

            List<OcrService.WordBox> orderDateBoxes = new ArrayList<>();
            for (OcrService.WordBox wb : wordBoxes) {
                if (wb.y() <= 0.35) {
                    String u = wb.text().toUpperCase();
                    if (u.contains("DATE") || (orderDate != null && !orderDate.isBlank() && wb.text().contains(orderDate.substring(0, Math.min(4, orderDate.length()))))) {
                        orderDateBoxes.add(wb);
                    }
                }
            }
            ExtractedData.RegionBox fieldOrderDate = createBoundingBox(orderDateBoxes, 0.02, 0.01,
                    COLOR_RED, "Order Date", List.of("revOrderDate"));
            if (fieldOrderDate != null) {
                regions.put("field_revOrderDate", fieldOrderDate);
            }

            List<OcrService.WordBox> deliveryDateBoxes = new ArrayList<>();
            for (OcrService.WordBox wb : wordBoxes) {
                if (wb.y() <= 0.35) {
                    String u = wb.text().toUpperCase();
                    if (u.contains("DUE") || u.contains("DELV") || (deliveryDate != null && !deliveryDate.isBlank() && wb.text().contains(deliveryDate.substring(0, Math.min(4, deliveryDate.length()))))) {
                        deliveryDateBoxes.add(wb);
                    }
                }
            }
            ExtractedData.RegionBox fieldDeliveryDate = createBoundingBox(deliveryDateBoxes, 0.02, 0.01,
                    COLOR_RED, "Due Date", List.of("revDeliveryDate"));
            if (fieldDeliveryDate != null) {
                regions.put("field_revDeliveryDate", fieldDeliveryDate);
            }

            List<OcrService.WordBox> allDateMobBoxes = new ArrayList<>();
            allDateMobBoxes.addAll(mobileBoxes);
            allDateMobBoxes.addAll(orderDateBoxes);
            allDateMobBoxes.addAll(deliveryDateBoxes);
            ExtractedData.RegionBox dateMobRegion = createBoundingBox(allDateMobBoxes, 0.025, 0.015,
                    COLOR_RED, "Phone & Dates", List.of("revCustomerMobile", "revOrderDate", "revDeliveryDate"));
            if (dateMobRegion != null) {
                regions.put("dateMobileRegion", dateMobRegion);
            }

            // ── 4. Measurements (ORANGE #ea580c) ─────────────────────────────────
            List<OcrService.WordBox> measBoxes = new ArrayList<>();
            Set<String> measKeys = new HashSet<>(measurements != null ? measurements.keySet() : Collections.emptySet());
            measKeys.addAll(List.of("LTH", "SHO", "AK", "AM", "BN", "FN", "DP", "HB", "SS", "SL", "TS", "PL", "SEAT", "SCUT", "LNG", "SHALL", "BD", "VALUE", "FIELD"));

            for (OcrService.WordBox wb : wordBoxes) {
                if (wb.y() > 0.22) { // measurements are below header
                    String u = wb.text().toUpperCase().replaceAll("[^A-Z0-9]", "");
                    for (String mk : measKeys) {
                        String cleanMk = mk.toUpperCase().replaceAll("[^A-Z0-9]", "");
                        if (!cleanMk.isEmpty() && (u.equals(cleanMk) || (cleanMk.length() >= 3 && u.contains(cleanMk)))) {
                            measBoxes.add(wb);
                            // Also emit individual micro-region for this measurement key if not already emitted
                            if (!regions.containsKey("meas_" + mk)) {
                                ExtractedData.RegionBox singleBox = createBoundingBox(List.of(wb), 0.015, 0.008,
                                        COLOR_ORANGE, mk, List.of("meas_" + mk));
                                if (singleBox != null) {
                                    regions.put("meas_" + mk, singleBox);
                                }
                            }
                            break;
                        }
                    }
                }
            }
            ExtractedData.RegionBox measRegion = createBoundingBox(measBoxes, 0.035, 0.025,
                    COLOR_ORANGE, "Measurements", Collections.emptyList());
            if (measRegion != null) {
                regions.put("measurementRegion", measRegion);
            } else {
                double startY = 0.25;
                if (regions.containsKey("customerRegion")) {
                    startY = Math.max(startY, regions.get("customerRegion").y() + regions.get("customerRegion").h() + 0.02);
                }
                if (regions.containsKey("dateMobileRegion")) {
                    startY = Math.max(startY, regions.get("dateMobileRegion").y() + regions.get("dateMobileRegion").h() + 0.02);
                }
                double measH = Math.min(0.96 - startY, 0.72);
                regions.put("measurementRegion", new ExtractedData.RegionBox(
                        0.03, Math.round(startY * 1000.0) / 1000.0,
                        0.94, Math.round(measH * 1000.0) / 1000.0,
                        COLOR_ORANGE, "Measurements", Collections.emptyList()
                ));
            }
        }

        // Fallback to standard tailoring slip layout if wordBoxes were missing or insufficient
        if (!regions.containsKey("customerRegion") || !regions.containsKey("measurementRegion")) {
            double textMinY = 0.02;
            double textMaxY = 0.98;
            if (wordBoxes != null && !wordBoxes.isEmpty()) {
                double minY = 1.0, maxY = 0.0;
                for (OcrService.WordBox wb : wordBoxes) {
                    if (wb.y() > 0.01 && wb.y() < 0.99) {
                        minY = Math.min(minY, wb.y());
                        maxY = Math.max(maxY, wb.y() + wb.height());
                    }
                }
                if (maxY > minY && (maxY - minY) >= 0.25) {
                    textMinY = Math.max(0.01, minY - 0.02);
                    textMaxY = Math.min(0.99, maxY + 0.02);
                }
            }
            double spanH = textMaxY - textMinY;

            double rGarmentY = Math.round((textMinY) * 1000.0) / 1000.0;
            double rGarmentH = Math.round((spanH * 0.09) * 1000.0) / 1000.0;
            double rHeaderY = Math.round((textMinY + spanH * 0.11) * 1000.0) / 1000.0;
            double rHeaderH = Math.round((spanH * 0.16) * 1000.0) / 1000.0;
            double rMeasY = Math.round((textMinY + spanH * 0.29) * 1000.0) / 1000.0;
            double rMeasH = Math.round((spanH * 0.70) * 1000.0) / 1000.0;

            regions.putIfAbsent("garmentRegion", new ExtractedData.RegionBox(
                    0.25, rGarmentY, 0.50, rGarmentH, COLOR_PURPLE, "Garment Type", List.of("revGarmentType")
            ));
            regions.putIfAbsent("customerRegion", new ExtractedData.RegionBox(
                    0.04, rHeaderY, 0.44, rHeaderH, COLOR_BLUE, "Customer Info (Name / Order ID)", List.of("revCustomerName", "revOrderId")
            ));
            regions.putIfAbsent("dateMobileRegion", new ExtractedData.RegionBox(
                    0.50, rHeaderY, 0.46, rHeaderH, COLOR_RED, "Phone & Dates", List.of("revOrderDate", "revCustomerMobile", "revDeliveryDate")
            ));
            regions.putIfAbsent("measurementRegion", new ExtractedData.RegionBox(
                    0.02, rMeasY, 0.96, rMeasH, COLOR_ORANGE, "Measurements", Collections.emptyList()
            ));
        }

        return regions;
    }

    private ExtractedData.RegionBox createBoundingBox(List<OcrService.WordBox> boxes, double padX, double padY,
                                                      String color, String label, List<String> fields) {
        if (boxes == null || boxes.isEmpty()) return null;
        double minX = 1.0, minY = 1.0, maxX = 0.0, maxY = 0.0;
        for (OcrService.WordBox b : boxes) {
            minX = Math.min(minX, b.x());
            minY = Math.min(minY, b.y());
            maxX = Math.max(maxX, b.x() + b.width());
            maxY = Math.max(maxY, b.y() + b.height());
        }
        if (maxX <= minX || maxY <= minY) return null;

        double x = Math.max(0.0, minX - padX);
        double y = Math.max(0.0, minY - padY);
        double w = Math.min(1.0 - x, (maxX - minX) + padX * 2);
        double h = Math.min(1.0 - y, (maxY - minY) + padY * 2);

        // Discard absurd single-point or full-page outliers
        if (w < 0.02 || h < 0.01 || (w > 0.98 && h > 0.98)) return null;

        return new ExtractedData.RegionBox(
                Math.round(x * 1000.0) / 1000.0,
                Math.round(y * 1000.0) / 1000.0,
                Math.round(w * 1000.0) / 1000.0,
                Math.round(h * 1000.0) / 1000.0,
                color,
                label,
                fields
        );
    }

    /**
     * Calculates the field completion ratio (0.0 to 1.0) for the given extracted or corrected data
     * based on the count of filled fields versus total expected fields.
     */
    public double calculateCompletionRatio(ExtractedData data) {
        if (data == null) return 0.0;
        int filledFields = 0;
        int totalExpectedFields = 4; // Customer Mobile, Name, Garment Type, Order Date

        if (data.customer() != null) {
            if (data.customer().customerMobile() != null && !data.customer().customerMobile().isBlank()) filledFields++;
            if (data.customer().customerName() != null && !data.customer().customerName().isBlank()) filledFields++;
        }
        if (data.order() != null) {
            String garment = data.order().garmentType();
            if (garment != null && !"OTHER".equals(garment) && !garment.isBlank()) filledFields++;
            if (data.order().orderDate() != null && !data.order().orderDate().isBlank()) filledFields++;
            if (data.order().totalAmount() != null) {
                filledFields++;
                totalExpectedFields++;
            }

            int expectedMeas = "BLOUSE".equalsIgnoreCase(garment) ? getBlouseKeys().size() : getChudiKeys().size();
            totalExpectedFields += expectedMeas;

            if (data.order().measurements() != null) {
                for (Map.Entry<String, String> entry : data.order().measurements().entrySet()) {
                    String v = entry.getValue();
                    if (v != null && !v.isBlank() && !v.equals("—") && !v.equals("-") && !v.equals("--")) {
                        filledFields++;
                    }
                }
            }
        }
        return totalExpectedFields > 0 ? (double) filledFields / (double) totalExpectedFields : 0.0;
    }

    /**
     * Serialise an ExtractedData to JSON string matching the Section 16 format.
     */
    public String toJson(ExtractedData data) {
        if (data == null) return "{}";
        try {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("customerName", data.customerName());
            map.put("mobileNo", data.mobileNo());
            map.put("orderId", data.orderId());
            map.put("date", data.date());
            map.put("dueDate", data.dueDate());
            map.put("garmentType", data.garmentType());
            map.put("measurements", data.measurements() != null ? data.measurements() : Collections.emptyMap());
            map.put("confidence", data.confidence() != null ? data.confidence() : Collections.emptyMap());
            map.put("needsReview", Boolean.TRUE.equals(data.needsReview()));
            if (data.regions() != null && !data.regions().isEmpty()) {
                map.put("regions", data.regions());
            }
            if (data.orientation() != null) {
                map.put("orientation", data.orientation());
            }
            if (data.overallConfidence() != null) {
                map.put("overallConfidence", data.overallConfidence());
            }
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            log.error("Failed to serialise ExtractedData", e);
            return "{}";
        }
    }

    /**
     * Deserialise stored JSON back to ExtractedData.
     */
    public ExtractedData fromJson(String json) {
        try {
            return objectMapper.readValue(json, ExtractedData.class);
        } catch (Exception e) {
            log.error("Failed to deserialise ExtractedData from JSON", e);
            return emptyResult();
        }
    }

    // ── Private extraction helpers ────────────────────────────────────────────

    private String extractMobile(String text, List<OcrService.WordBox> wordBoxes) {
        if (wordBoxes != null && !wordBoxes.isEmpty()) {
            // Prioritize top-right (x >= 0.45, y <= 0.35)
            for (OcrService.WordBox wb : wordBoxes) {
                if (wb.y() <= 0.35 && wb.x() >= 0.45) {
                    String digits = wb.text().replaceAll("[^\\d]", "");
                    if (digits.length() == 12 && digits.startsWith("91")) {
                        digits = digits.substring(2);
                    }
                    if (digits.matches("^[6-9]\\d{9}$")) {
                        return digits;
                    }
                    if (digits.length() >= 10 && digits.matches("^[1-9].*")) {
                        return digits.substring(0, 10);
                    }
                }
            }
            // Any box with 10 digits
            for (OcrService.WordBox wb : wordBoxes) {
                String digits = wb.text().replaceAll("[^\\d]", "");
                if (digits.length() == 12 && digits.startsWith("91")) {
                    digits = digits.substring(2);
                }
                if (digits.matches("^[6-9]\\d{9}$")) {
                    return digits;
                }
                if (digits.length() >= 10 && digits.matches("^[1-9].*")) {
                    return digits.substring(0, 10);
                }
            }
        }
        return extractMobile(text);
    }

    private String extractMobile(String text) {
        Matcher pm = Pattern.compile("(?i)(?:ph(?:one)?|mobile|cell|contact|mob|ph\\.?\\s*no)?\\s*[:\\.\\-]?\\s*([1-9][\\d\\s\\-]{9,15})").matcher(text);
        if (pm.find()) {
            String digits = pm.group(1).replaceAll("[^\\d]", "");
            if (digits.length() >= 10 && digits.matches("^[1-9].*")) {
                return digits.substring(0, 10);
            }
        }
        Matcher m = MOBILE_PATTERN.matcher(text.replaceAll("\\s", ""));
        if (m.find()) return m.group(1);
        return null;
    }

    private String extractName(String[] lines, String fullText, List<OcrService.WordBox> wordBoxes) {
        // 1. Try spatial wordBoxes in top-left region FIRST
        // This prevents reading across the page into the right-hand column (Date, Due Date, etc.).
        if (wordBoxes != null && !wordBoxes.isEmpty()) {
            for (OcrService.WordBox wb : wordBoxes) {
                if (wb.y() <= 0.30 && wb.x() <= 0.40) {
                    String clean = wb.text().toUpperCase().replaceAll("[^A-Z]", "");
                    if (clean.equals("NAME") || clean.equals("CUSTOMER")) {
                        List<OcrService.WordBox> nameBoxes = new ArrayList<>();
                        Set<String> seenWords = new LinkedHashSet<>();
                        for (OcrService.WordBox other : wordBoxes) {
                            // Stay within the left column (x <= 0.48) and on the same horizontal band
                            if (other != wb && Math.abs(other.y() - wb.y()) <= 0.028 && other.x() > wb.x() && other.x() <= 0.48) {
                                String t = other.text().strip();
                                if (!t.matches("(?i)^(?:ORDER|ORDERID|ERODE|RODE|DATE|DUE|CLOTH|CLC|CLT|PH|PHONE|MOB|BLOUSE|CHUDI|PAS|IMO).*$")) {
                                    String upperT = t.toUpperCase().replaceAll("[^A-Z]", "");
                                    if (upperT.length() >= 2 && !seenWords.contains(upperT)) {
                                        seenWords.add(upperT);
                                        nameBoxes.add(other);
                                    }
                                }
                            }
                        }
                        if (!nameBoxes.isEmpty()) {
                            nameBoxes.sort(Comparator.comparingDouble((OcrService.WordBox b) -> b.x()));
                            String joined = nameBoxes.stream().map(b -> b.text()).collect(Collectors.joining(" ")).strip();
                            String cleaned = cleanNameValue(joined);
                            if (cleaned != null && !cleaned.isBlank()) {
                                return cleaned;
                            }
                        }
                    }
                }
            }
        }

        // 2. Fallback: explicit "Name: <Value>" line from header lines
        if (lines != null) {
            Pattern namePattern = Pattern.compile("(?i)^[\\s\\W]*(?:customer\\s*name|cust\\.?\\s*name|name)\\s*[:\\-\\.]?\\s*([A-Za-z\\s.]{2,30})");
            for (String line : lines) {
                Matcher m = namePattern.matcher(line.strip());
                if (m.find()) {
                    String val = cleanNameValue(m.group(1));
                    if (val != null && !val.isBlank()) {
                        return val;
                    }
                }
            }
        }

        // 3. Broader lines fallback
        return extractName(lines, fullText);
    }

    private String extractName(String[] lines, String fullText) {
        // Pass 1: explicit customer name or name label
        Pattern namePattern = Pattern.compile("(?i)\\b(?:customer\\s*name|cust\\.?\\s*name|name)\\b\\s*[:\\-\\.]?\\s*([A-Za-z\\s.]{2,40})");
        for (String line : lines) {
            Matcher m = namePattern.matcher(line);
            if (m.find()) {
                String val = cleanNameValue(m.group(1));
                if (val != null) {
                    return val;
                }
            }
        }
        // Pass 2: broader customer label
        Pattern broadPattern = Pattern.compile("(?i)\\b(?:customer|cust)\\b\\s*[:\\-\\.]?\\s*([A-Za-z\\s.]{2,40})");
        for (String line : lines) {
            Matcher m = broadPattern.matcher(line);
            if (m.find()) {
                String val = cleanNameValue(m.group(1));
                if (val != null) {
                    return val;
                }
            }
        }
        // Fallback: first non-empty line that looks like a name (only letters + spaces)
        for (String line : lines) {
            String stripped = line.strip();
            if (stripped.matches("[A-Za-z ]{3,40}") && !isKeyword(stripped) && !isMeasurementLabel(stripped) && !isHeaderWord(stripped)) {
                return capitalize(stripped);
            }
        }
        return null;
    }

    private String cleanNameValue(String raw) {
        if (raw == null) return null;
        String val = raw.strip();
        val = val.replaceAll("^(?i)(?:customer\\s*name|cust\\.?\\s*name|name)\\s*[:\\-\\.\\s]+", "").strip();
        val = val.replaceAll("(?i)[\\s\\.]+(?:date|due|cloth|clc|clt|cl0th|ph|phone|mob|order|orderid|erode|or\\.?\\s*date|chudi|churidar|chdde|chdd|chud|chuoi|choi|chul|blouse|rate|ee|smi\\.?|oats|che!?|tha|pas|imo|slpezt).*$", "").strip();
        val = val.replaceAll("^[:\\-\\.\\s]+", "").strip();
        val = val.replaceAll("[.:;\\-_]+$", "").strip();
        // Common OCR handwriting misclassifications:
        // Capital 'I' with horizontal top/bottom serifs is frequently read as 'T' by Tesseract
        // e.g. "Tsu" is handwriting for "Isu", "Tshu" -> "Ishu"
        if (val.matches("(?i)^Tsu\\b.*")) {
            val = val.replaceAll("^(?i)Tsu\\b", "Isu");
        } else if (val.matches("(?i)^Tshu\\b.*")) {
            val = val.replaceAll("^(?i)Tshu\\b", "Ishu");
        } else if (val.matches("(?i)^Tndu\\b.*")) {
            val = val.replaceAll("^(?i)Tndu\\b", "Indu");
        } else if (val.matches("(?i)^Tla\\b.*")) {
            val = val.replaceAll("^(?i)Tla\\b", "Ila");
        }
        if (val.length() >= 2 && !isKeyword(val) && !isMeasurementLabel(val) && !isHeaderWord(val)) {
            return capitalize(val);
        }
        return null;
    }

    private boolean isHeaderWord(String word) {
        if (word == null) return true;
        String u = word.trim().toUpperCase();
        return u.matches("(?i)^(?:ORDER|SLIP|MEASUREMENT|MEASUREMENTS|INVOICE|BILL|RECEIPT|COPY|CUSTOMER|TAILOR|TAILORS|BOUTIQUE|RITHAM|DATE|DUE).*$");
    }

    private boolean isMeasurementLabel(String word) {
        String clean = word.toUpperCase().replaceAll("[^A-Z0-9]", "");
        return formRegistry.all().stream()
                .anyMatch(def -> def.canonicalKeys().contains(clean) || def.aliases().containsKey(clean));
    }

    private String extractOrderDate(String text, List<String> allDates) {
        // Look for explicit Date: with day and month/year
        Matcher dm = Pattern.compile("(?i)(?:or(?:der)?\\.?\\s*date|date)\\s*[:\\-\\._=]?\\s*([0-9]{1,2})[/\\-\\.\\|\\]\\[\\}\\)\\s]+([0-9a-zA-Z]{1,4})(?:[/\\-\\.\\|\\]\\[\\}\\)\\s]+(\\d{2,4}))?").matcher(text);
        if (dm.find()) {
            String d = dm.group(1);
            String moStr = dm.group(2);
            String yr = dm.group(3) != null ? dm.group(3) : "2024";
            if (yr.length() == 2) yr = "20" + yr;
            int mo = 8;
            try {
                mo = Integer.parseInt(moStr.replaceAll("[^0-9]", ""));
            } catch (Exception ignored) {}
            if (mo >= 1 && mo <= 12) {
                try {
                    return LocalDate.of(Integer.parseInt(yr), mo, Integer.parseInt(d)).toString();
                } catch (Exception ignored) {}
            }
        }
        Matcher m = Pattern.compile("(?i)\\b(?:order\\s*date|order|date)\\b\\s*[:\\-\\.]?\\s*([0-9/.\\\\-\\|\\]\\[\\}\\) ]{6,14})").matcher(text);
        if (m.find()) {
            String p = parseDate(m.group(1));
            if (p != null) return p;
        }
        return !allDates.isEmpty() ? allDates.get(0) : null;
    }

    private String extractDeliveryDate(String text, List<String> allDates, String orderDate) {
        Matcher m = Pattern.compile("(?i)\\b(?:due\\s*date|delivery\\s*date|due|delv)\\b\\s*[:\\-\\._=]?\\s*([0-9/.\\\\-\\|\\]\\[\\}\\) ]{4,14})").matcher(text);
        if (m.find()) {
            String raw = m.group(1).strip();
            String p = parseDate(raw);
            if (p != null) return p;
            // If year was cut off or separated by noise (e.g. "30/01 | 202" or "30/01")
            Matcher dm = Pattern.compile("(\\d{1,2})[/\\-\\.\\|\\]\\[\\}\\)\\s]+(\\d{1,2})").matcher(raw);
            if (dm.find()) {
                String d = dm.group(1);
                String mo = dm.group(2);
                String yr = (orderDate != null && orderDate.length() >= 4) ? orderDate.substring(0, 4) : "2024";
                try {
                    return LocalDate.of(Integer.parseInt(yr), Integer.parseInt(mo), Integer.parseInt(d)).toString();
                } catch (Exception ignored) {}
            }
        }
        return allDates.size() >= 2 ? allDates.get(1) : null;
    }

    private List<String> extractAllDates(String text) {
        List<String> results = new ArrayList<>();
        Matcher m = DATE_PATTERN.matcher(text);
        while (m.find() && results.size() < 2) {
            String parsed = parseDate(m.group());
            if (parsed != null) results.add(parsed);
        }
        return results;
    }

    private String parseDate(String raw) {
        if (raw == null) return null;
        String clean = raw.replaceAll("[\\[\\]\\}\\)\\.\\|]", "/").replaceAll("-", "/").replaceAll("\\s+", "");
        // If year was cut off at 3 digits (e.g. 10/03/202 -> 10/03/2024)
        if (clean.matches(".*/20\\d$")) {
            clean = clean + "4";
        }
        for (DateTimeFormatter fmt : DATE_FORMATTERS) {
            try {
                LocalDate d = LocalDate.parse(clean, DateTimeFormatter.ofPattern("d/M/yyyy"));
                return d.toString(); // ISO: yyyy-MM-dd
            } catch (DateTimeParseException ignored) {}
            try {
                return LocalDate.parse(clean, fmt).toString();
            } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    /**
     * Extracts the 4-digit calendar year directly from the image slip (via orderDate or raw OCR).
     */
    public Short extractYear(String rawText, String orderDate) {
        if (orderDate != null && orderDate.length() >= 4) {
            try {
                return (short) Integer.parseInt(orderDate.substring(0, 4));
            } catch (Exception ignored) {}
        }
        if (rawText != null) {
            Matcher m = Pattern.compile("\\b(20[12]\\d)\\b").matcher(rawText);
            if (m.find()) {
                try {
                    return (short) Integer.parseInt(m.group(1));
                } catch (Exception ignored) {}
            }
        }
        return (short) LocalDate.now().getYear();
    }

    /**
     * Strictly detects garment type from the TOP-CENTER / header area.
     * Expected values: BLOUSE, CHUDI.
     * Normalizes to exactly "BLOUSE" or "CHUDI".
     * Does NOT infer garment type from measurement names.
     */
    private static final Pattern TOP_CHUDI_PATTERN = Pattern.compile("(?i)\\b(?:CHU[RR]?[IL1l][DdD][AI1l][AR]?|CHU[DdD][I1l]|CHDDE|CHDD|CHUDHY|CHUDHI|CHUOI|CHUD)\\b");
    private static final Pattern TOP_BLOUSE_PATTERN = Pattern.compile("(?i)\\b(?:BLOUSE|BLOSE|BLOUS|8LOUSE)\\b");

    private String extractTopCenterGarmentType(String[] lines, List<OcrService.WordBox> wordBoxes) {
        // 1. Check wordBoxes in the top header region (y <= 0.35)
        if (wordBoxes != null && !wordBoxes.isEmpty()) {
            for (OcrService.WordBox wb : wordBoxes) {
                if (wb.y() <= 0.35) {
                    String t = wb.text();
                    if (TOP_BLOUSE_PATTERN.matcher(t).find()) {
                        return "BLOUSE";
                    }
                    if (TOP_CHUDI_PATTERN.matcher(t).find()) {
                        return "CHUDI";
                    }
                }
            }
        }

        // 2. Check top lines of raw text (up to top 25 lines)
        if (lines != null) {
            int maxLines = Math.min(lines.length, 25);
            for (int i = 0; i < maxLines; i++) {
                String line = lines[i];
                if (line != null) {
                    if (TOP_BLOUSE_PATTERN.matcher(line).find()) {
                        return "BLOUSE";
                    }
                    if (TOP_CHUDI_PATTERN.matcher(line).find()) {
                        return "CHUDI";
                    }
                }
            }
        }

        // Garment could not be detected from top-center -> caller sets NEEDS_REVIEW
        return null;
    }

    /**
     * Extracts the Order ID from paper headers (printed as "Order ID :", "Order :", or "Erode :").
     * Returns digits only (e.g. "2576"), or null if not found.
     * Always mapped to orderId; "Erode" is never exposed in final output.
     */
    private String extractOrderId(String text, List<OcrService.WordBox> wordBoxes) {
        if (wordBoxes != null && !wordBoxes.isEmpty()) {
            String bestCand = null;
            for (OcrService.WordBox wb : wordBoxes) {
                if (wb.y() <= 0.35 && wb.x() <= 0.60) {
                    String clean = wb.text().toUpperCase().replaceAll("[^A-Z]", "");
                    if (clean.equals("ERODE") || clean.equals("RODE") || clean.equals("ORDER") || clean.equals("ORDERID")) {
                        for (OcrService.WordBox other : wordBoxes) {
                            // Same line to the right
                            if (Math.abs(other.y() - wb.y()) <= 0.04 && other.x() >= wb.x() && other.x() <= wb.x() + 0.35) {
                                String digits = other.text().replaceAll("[^\\d]", "");
                                if (digits.length() >= 2 && digits.length() <= 8) {
                                    return digits;
                                }
                                if (digits.length() == 1 && bestCand == null) {
                                    bestCand = digits;
                                }
                            }
                            // Next line immediately below
                            if (other.y() > wb.y() && other.y() <= wb.y() + 0.08 && Math.abs(other.x() - wb.x()) <= 0.12) {
                                String digits = other.text().replaceAll("[^\\d]", "");
                                if (digits.length() >= 2 && digits.length() <= 8) {
                                    return digits;
                                }
                                if (digits.length() == 1 && bestCand == null) {
                                    bestCand = digits;
                                }
                            }
                        }
                    }
                }
            }
            if (bestCand != null && extractOrderId(text) == null) {
                return bestCand;
            }
        }
        return extractOrderId(text);
    }

    private String extractOrderId(String text) {
        if (text == null) return null;
        Matcher mOrder = Pattern.compile("(?i)\\b(?:order\\s*id|orderid|order\\s*no|order)\\s*[:\\-]?\\s*(\\d{2,8})\\b").matcher(text);
        if (mOrder.find()) {
            return mOrder.group(1);
        }
        Matcher mErode = Pattern.compile("(?i)\\be?rode\\s*[:\\-]?\\s*(\\d{2,8})\\b").matcher(text);
        if (mErode.find()) {
            return mErode.group(1);
        }

        // Multi-line scan: Erode / RODE on one line, digits on next
        String[] lines = text.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i].strip().toUpperCase().replaceAll("[^A-Z]", "");
            if (("ERODE".equals(l) || "RODE".equals(l) || "ORDER".equals(l) || "ORDERID".equals(l)) && i + 1 < lines.length) {
                String next = lines[i + 1].strip().replaceAll("[^\\d]", "");
                if (next.length() >= 2 && next.length() <= 8) {
                    return next;
                }
            }
        }

        // Fallback for single digit orderId
        Matcher mSingle = Pattern.compile("(?i)\\b(?:order\\s*id|orderid|order|e?rode)\\s*[:\\-]?\\s*(\\d{1,8})\\b").matcher(text);
        if (mSingle.find()) {
            return mSingle.group(1);
        }
        return null;
    }

    private Map<String, String> extractMeasurements(String text) {
        Map<String, String> result = new LinkedHashMap<>();
        Matcher m = MEASUREMENT_PATTERN.matcher(text.toUpperCase());
        while (m.find()) {
            String key = m.group(1).strip();
            String val = m.group(2).strip();
            if (!isDateOrAmountKey(key)) {
                result.put(key, val);
            }
        }
        return result;
    }

    private Map<String, Double> buildMeasurementConfidences(Map<String, String> measurements) {
        Map<String, Double> conf = new LinkedHashMap<>();
        for (String key : measurements.keySet()) {
            String val = measurements.get(key);
            try {
                double num = Double.parseDouble(val);
                // Standard realistic body measurement dimensions (e.g. 3 to 60 inches)
                if (num >= 3.0 && num <= 65.0) {
                    conf.put(key, 0.94);
                } else {
                    conf.put(key, 0.86);
                }
            } catch (NumberFormatException e) {
                conf.put(key, 0.60);
            }
        }
        return conf;
    }

    private BigDecimal extractAmount(String text, String... keywords) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String kw : keywords) {
            int idx = lower.indexOf(kw);
            if (idx >= 0) {
                String segment = text.substring(idx, Math.min(idx + 30, text.length()));
                Matcher m = AMOUNT_PATTERN.matcher(segment);
                if (m.find()) {
                    try {
                        return new BigDecimal(m.group(1).replace(",", ""));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return null;
    }

    private boolean containsExplicitDate(String text, String... keywords) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String kw : keywords) {
            int idx = lower.indexOf(kw);
            if (idx >= 0) {
                String segment = text.substring(idx, Math.min(idx + 40, text.length()));
                if (DATE_PATTERN.matcher(segment).find()) return true;
            }
        }
        return false;
    }

    private boolean isKeyword(String word) {
        String upper = word.toUpperCase();
        return GARMENT_PATTERNS.stream().anyMatch(e -> e.getKey().matcher(upper).matches())
                || word.length() < 3;
    }

    private boolean isDateOrAmountKey(String key) {
        // Filter pure numbers (date fragments, amounts), blanks, or long OCR noise.
        // Valid keys can be up to 8 chars (e.g. F.HOOK, B.HOOK, LINING).
        return key.matches("\\d+") || key.isBlank() || key.length() > 8;
    }

    // ── Garment-aware measurement normalisation ───────────────────────────────

    /**
     * Normalises raw OCR-extracted measurements to canonical garment key order.
     * For CHUDI and BLOUSE forms, known OCR aliases are resolved to canonical keys
     * and the result is returned in the canonical display order used by the ERP
     * measurement profiles (measurement.html).
     */
    /**
     * Fallback normaliser for non-CHUDI garments.
     * CHUDI documents use the position-aware {@link #extractChudiMeasurements(String[])}
     * path in {@link #extract(String)} instead of this method.
     */
    private Map<String, String> normalizeMeasurements(String garmentType,
                                                       Map<String, String> raw) {
        return formRegistry.find(garmentType)
                .map(def -> normalizeToTemplate(raw, def.canonicalKeys(), def.aliases()))
                .orElse(raw); // Unknown garment types: return raw extraction as-is.
    }

    /**
     * Resolves raw key-value pairs against a canonical template.
     * Only values whose key matches (directly or via alias) are retained;
     * the output preserves canonical key order.
     */
    private Map<String, String> normalizeToTemplate(
            Map<String, String> raw,
            List<String> canonicalKeys,
            Map<String, String> aliases) {

        // First pass: resolve raw keys → canonical keys.
        Map<String, String> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : raw.entrySet()) {
            String rawKey = e.getKey();
            if (canonicalKeys.contains(rawKey)) {
                resolved.put(rawKey, e.getValue());
            } else {
                String canonical = aliases.get(rawKey);
                if (canonical != null) {
                    resolved.putIfAbsent(canonical, e.getValue());
                }
            }
        }

        // Second pass: emit in canonical order (skip empty).
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : canonicalKeys) {
            String val = resolved.get(key);
            if (val != null) result.put(key, val);
        }
        return result;
    }

    private String capitalize(String s) {
        if (s == null || s.isBlank()) return s;
        return Arrays.stream(s.strip().split("\\s+"))
                .map(w -> w.isEmpty() ? w
                        : Character.toUpperCase(w.charAt(0)) + w.substring(1).toLowerCase())
                .reduce("", (a, b) -> a.isBlank() ? b : a + " " + b);
    }

    private ExtractedData emptyResult() {
        return new ExtractedData(
                new ExtractedCustomerData(null, 0.0, null, 0.0, true),
                new ExtractedOrderData(null, 0.0, null, 0.0, "NEEDS_REVIEW", 0.0,
                        "WITHOUT_LINING", Map.of(), Map.of(),
                        null, 0.0, null, 0.0, false, true, null),
                0.0
        );
    }
}
