package com.ritham.erp.module.migration.service.ocr;

import com.ritham.erp.module.migration.service.OcrService.WordBox;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts header metadata (Customer Name, Order ID from Erode, Mobile, Dates)
 * specifically from TOP_LEFT and TOP_RIGHT cropped regions.
 */
@Component
@Slf4j
public class HeaderExtractionService {

    public record HeaderFields(
            String customerName,
            Double customerNameConfidence,
            WordBox customerNameBox,

            String orderId,
            Double orderIdConfidence,
            WordBox orderIdBox,

            String mobileNo,
            Double mobileNoConfidence,
            WordBox mobileNoBox,

            String date,
            Double dateConfidence,
            WordBox dateBox,

            String dueDate,
            Double dueDateConfidence,
            WordBox dueDateBox
    ) {}

    private static final Pattern MOBILE_PATTERN =
            Pattern.compile("(?<![\\d])([6-9]\\d{9})(?![\\d])");

    private static final Pattern DATE_PATTERN =
            Pattern.compile("(\\d{1,2})[/\\-\\.\\|\\]\\[](\\d{1,2})[/\\-\\.\\|\\]\\[](\\d{2,4})");

    private static final List<DateTimeFormatter> FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("d.M.yyyy"),
            DateTimeFormatter.ofPattern("d/M/yy"),
            DateTimeFormatter.ofPattern("d-M-yy")
    );

    /**
     * Extracts header fields from dedicated crops.
     */
    public HeaderFields extractHeaders(
            BufferedImage topLeftCrop, RegionExtractor.NormalizedRect topLeftBounds,
            BufferedImage topRightCrop, RegionExtractor.NormalizedRect topRightBounds,
            OcrEngine ocrEngine) {

        // ── 1. Top-Left: Customer Name & Order ID (Erode) ──────────────────────
        String name = null;
        Double nameConf = 0.0;
        WordBox nameBox = null;

        String orderId = null;
        Double orderIdConf = 0.0;
        WordBox orderIdBox = null;

        if (topLeftCrop != null) {
            OcrEngine.OcrEngineResult tlRes = ocrEngine.ocrRegion(
                    topLeftCrop, 0.0, 0.0, 1.0, 1.0, "eng", net.sourceforge.tess4j.ITessAPI.TessPageSegMode.PSM_SPARSE_TEXT);
            String tlText = tlRes.text();
            List<WordBox> tlBoxes = tlRes.wordBoxes();

            // Extract Name:
            name = parseName(tlText, tlBoxes);
            if (name != null) {
                nameConf = 0.95;
                nameBox = findWordBox(tlBoxes, name, topLeftBounds);
            }

            // Extract Order ID (printed as Erode : or Order ID :)
            orderId = parseOrderId(tlText, tlBoxes);
            if (orderId != null) {
                orderIdConf = 0.98;
                orderIdBox = findWordBox(tlBoxes, orderId, topLeftBounds);
            }
        }

        // ── 2. Top-Right: Mobile, Order Date, Due Date ─────────────────────────
        String mobile = null;
        Double mobileConf = 0.0;
        WordBox mobileBox = null;

        String date = null;
        Double dateConf = 0.0;
        WordBox dateBox = null;

        String dueDate = null;
        Double dueDateConf = 0.0;
        WordBox dueDateBox = null;

        if (topRightCrop != null) {
            OcrEngine.OcrEngineResult trRes = ocrEngine.ocrRegion(
                    topRightCrop, 0.0, 0.0, 1.0, 1.0, "eng", net.sourceforge.tess4j.ITessAPI.TessPageSegMode.PSM_SPARSE_TEXT);
            String trText = trRes.text();
            List<WordBox> trBoxes = trRes.wordBoxes();

            // Mobile No.
            mobile = parseMobile(trText, trBoxes);
            if (mobile != null) {
                mobileConf = 0.97;
                mobileBox = findWordBox(trBoxes, mobile, topRightBounds);
            }

            // Order Date
            date = parseOrderDate(trText, trBoxes);
            if (date != null) {
                dateConf = 0.90;
                dateBox = findWordBox(trBoxes, "Date", topRightBounds);
            }

            // Due Date
            dueDate = parseDueDate(trText, trBoxes, date);
            if (dueDate != null) {
                dueDateConf = 0.90;
                dueDateBox = findWordBox(trBoxes, "Due", topRightBounds);
            }
        }

        return new HeaderFields(
                name, nameConf, nameBox,
                orderId, orderIdConf, orderIdBox,
                mobile, mobileConf, mobileBox,
                date, dateConf, dateBox,
                dueDate, dueDateConf, dueDateBox
        );
    }

    private String parseName(String text, List<WordBox> boxes) {
        if (text == null) return null;
        Matcher m = Pattern.compile("(?i)\\b(?:name|customer|cust)\\b\\s*[:\\-\\.]?\\s*([A-Za-z\\s.]{2,35})").matcher(text);
        if (m.find()) {
            String val = cleanName(m.group(1));
            if (val != null) return val;
        }

        // Check word boxes after NAME label
        if (boxes != null) {
            for (int i = 0; i < boxes.size(); i++) {
                String w = boxes.get(i).text().toUpperCase().replaceAll("[^A-Z]", "");
                if ("NAME".equals(w) && i + 1 < boxes.size()) {
                    List<String> parts = new ArrayList<>();
                    for (int j = i + 1; j < boxes.size(); j++) {
                        String cand = boxes.get(j).text().strip();
                        if (cand.matches("(?i)^(?:ERODE|ORDER|DATE|DUE|CHUDI|BLOUSE|PH).*$")) break;
                        if (cand.matches("[A-Za-z.]+")) parts.add(cand);
                    }
                    if (!parts.isEmpty()) {
                        String joined = cleanName(String.join(" ", parts));
                        if (joined != null) return joined;
                    }
                }
            }
        }
        return null;
    }

    private String cleanName(String raw) {
        if (raw == null) return null;
        String s = raw.strip()
                .replaceAll("(?i)\\s+(?:erode|order|date|due|chudi|blouse|ph|pas|imo|slpezt).*$", "")
                .replaceAll("^[:\\-\\.\\s]+", "").strip();
        if (s.matches("(?i)^Tsu\\b.*")) {
            s = s.replaceAll("^(?i)Tsu\\b", "Isu");
        }
        if (s.length() >= 2 && !s.matches("(?i)^(?:NAME|ERODE|ORDER|DATE|DUE|BLOUSE|CHUDI|PH)$")) {
            // Capitalize
            String[] words = s.split("\\s+");
            StringBuilder sb = new StringBuilder();
            for (String w : words) {
                if (w.length() > 0) {
                    sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase()).append(" ");
                }
            }
            return sb.toString().trim();
        }
        return null;
    }

    private String parseOrderId(String text, List<WordBox> boxes) {
        if (text == null) return null;
        Matcher mErode = Pattern.compile("(?i)\\b(?:erode|order\\s*id|order)\\s*[:\\-]?\\s*(\\d{1,8})\\b").matcher(text);
        if (mErode.find()) {
            return mErode.group(1);
        }
        if (boxes != null) {
            for (int i = 0; i < boxes.size(); i++) {
                String w = boxes.get(i).text().toUpperCase().replaceAll("[^A-Z]", "");
                if (("ERODE".equals(w) || "ORDER".equals(w) || "ORDERID".equals(w)) && i + 1 < boxes.size()) {
                    String d = boxes.get(i + 1).text().replaceAll("[^\\d]", "");
                    if (d.length() >= 1 && d.length() <= 8) return d;
                }
            }
        }
        return null;
    }

    private String parseMobile(String text, List<WordBox> boxes) {
        if (boxes != null) {
            for (WordBox b : boxes) {
                String d = b.text().replaceAll("[^\\d]", "");
                if (d.length() == 12 && d.startsWith("91")) d = d.substring(2);
                if (d.matches("^[6-9]\\d{9}$")) return d;
                if (d.length() >= 10 && d.matches("^[6-9].*")) return d.substring(0, 10);
            }
        }
        if (text == null) return null;
        Matcher pm = Pattern.compile("(?i)(?:ph(?:one)?|mobile|ph\\.?\\s*no|cell|mob)?\\s*[:\\.\\-]?\\s*([6-9][\\d\\s\\-]{9,15})").matcher(text);
        if (pm.find()) {
            String digits = pm.group(1).replaceAll("[^\\d]", "");
            if (digits.length() >= 10 && digits.matches("^[6-9].*")) return digits.substring(0, 10);
        }
        Matcher m = MOBILE_PATTERN.matcher(text.replaceAll("\\s", ""));
        if (m.find()) return m.group(1);
        return null;
    }

    private String parseOrderDate(String text, List<WordBox> boxes) {
        if (text == null) return null;
        // Prefer explicit "Or. Date :" or "Date :" line
        Matcher m = Pattern.compile("(?i)(?:or(?:der)?\\.?\\s*date|date)\\s*[:\\-\\._=]?\\s*([0-9/.\\\\-\\|\\]\\[ ]{6,14})").matcher(text);
        if (m.find()) {
            String parsed = parseIsoDate(m.group(1));
            if (parsed != null) return parsed;
        }

        List<String> all = extractAllDates(text);
        return !all.isEmpty() ? all.get(0) : null;
    }

    private String parseDueDate(String text, List<WordBox> boxes, String orderDate) {
        if (text == null) return null;
        Matcher m = Pattern.compile("(?i)(?:due\\s*date|due|delv)\\s*[:\\-\\._=]?\\s*([0-9/.\\\\-\\|\\]\\[ ]{6,14})").matcher(text);
        if (m.find()) {
            String parsed = parseIsoDate(m.group(1));
            if (parsed != null) return parsed;
        }

        List<String> all = extractAllDates(text);
        if (all.size() >= 2) return all.get(1);
        return null;
    }

    private List<String> extractAllDates(String text) {
        List<String> dates = new ArrayList<>();
        Matcher m = DATE_PATTERN.matcher(text);
        while (m.find() && dates.size() < 2) {
            String parsed = parseIsoDate(m.group());
            if (parsed != null) dates.add(parsed);
        }
        return dates;
    }

    private String parseIsoDate(String raw) {
        if (raw == null) return null;
        // Normalize noise: pipes, brackets, periods to slash
        String clean = raw.replaceAll("[\\[\\]\\.\\|]", "/").replaceAll("-", "/").replaceAll("\\s+", "");
        for (DateTimeFormatter fmt : FORMATTERS) {
            try {
                LocalDate d = LocalDate.parse(clean, DateTimeFormatter.ofPattern("d/M/yyyy"));
                return d.toString();
            } catch (DateTimeParseException ignored) {}
            try {
                return LocalDate.parse(clean, fmt).toString();
            } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    private WordBox findWordBox(List<WordBox> boxes, String keyword, RegionExtractor.NormalizedRect bounds) {
        if (boxes == null || keyword == null) return null;
        for (WordBox b : boxes) {
            if (b.text().toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT))) {
                double gx = bounds.x() + b.x() * bounds.width();
                double gy = bounds.y() + b.y() * bounds.height();
                double gw = b.width() * bounds.width();
                double gh = b.height() * bounds.height();
                return new WordBox(b.text(), gx, gy, gw, gh, b.confidence());
            }
        }
        return null;
    }
}
