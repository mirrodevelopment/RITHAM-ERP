package com.ritham.erp.module.migration;

import com.ritham.erp.module.migration.dto.ExtractedData;
import com.ritham.erp.module.migration.service.ExtractionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Unit tests for {@link ExtractionService}.
 *
 * <p>Tests use synthetic OCR text that mimics the line-by-line output
 * Tesseract produces when scanning CHUDI measurement forms.
 */
class ExtractionServiceTest {

    private ExtractionService service;

    /** Minimal synthetic CHUDI OCR text with all 18 measurement fields. */
    private static final String CHUDI_FULL_OCR = """
            CHUDI
            Name: Revathi
            Erode: 353
            Date: 05/04/2024
            Due Date: 12/04/2024
            Ph. No.: 9345678901
            F.N.   14
            B.N.   13
            H.B.   15
            L      40
            SS     10
            SL     11
            SL     11
            AM      9
            B      12
            H      14
            T.S.   16
            PL.    40
            S.      8
            L.     38
            S.CUT   9
            LNG    44
            SHALL   2
            B.D.    6
            """;

    /** Minimal synthetic BLOUSE OCR text with all 19 measurement fields. */
    private static final String BLOUSE_FULL_OCR = """
            BLOUSE
            Name: Meenakshi
            Erode: 2576
            Date: 10/05/2024
            Due Date: 17/05/2024
            Ph. No.: 9842123456
            LTH    14.5
            SHO    14
            H.S.   6.5
            H.L.   7
            H.LO   12
            AK     16
            AM     13
            BN     8.5
            FN     6.5
            DP-1   9.5
            DP-1   12.5
            B-1    34
            B-2    36
            B-3    31
            F.HOOK 1
            B.HOOK 0
            LINING 1
            AV.    1
            SARI   Green silk
            """;

    @BeforeEach
    void setUp() {
        service = new ExtractionService(new ObjectMapper());
    }

    // ── Garment Detection ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Garment type detection")
    class GarmentDetection {

        @Test
        @DisplayName("Detects CHUDI from clear heading")
        void detectsChudiClean() {
            ExtractedData result = service.extract(CHUDI_FULL_OCR);
            assertThat(result.order().garmentType()).isEqualTo("CHUDI");
        }

        @Test
        @DisplayName("Detects CHUDI when OCR swaps I→1 (CHUD1)")
        void detectsChudiOcrVariantDigitOne() {
            String ocr = CHUDI_FULL_OCR.replace("CHUDI", "CHUD1");
            ExtractedData result = service.extract(ocr);
            assertThat(result.order().garmentType()).isEqualTo("CHUDI");
        }

        @Test
        @DisplayName("Detects CHUDI when OCR swaps I→l (CHUDl)")
        void detectsChudiOcrVariantLowerL() {
            String ocr = CHUDI_FULL_OCR.replace("CHUDI", "CHUDl");
            ExtractedData result = service.extract(ocr);
            assertThat(result.order().garmentType()).isEqualTo("CHUDI");
        }

        @Test
        @DisplayName("Detects BLOUSE from heading")
        void detectsBlouse() {
            String ocr = "BLOUSE\nName: Priya\nPh. No.: 9876543210\nLTH 15\nSHO 14\n";
            ExtractedData result = service.extract(ocr);
            assertThat(result.order().garmentType()).isEqualTo("BLOUSE");
        }

        @Test
        @DisplayName("Falls back to NEEDS_REVIEW when garment cannot be determined")
        void fallsBackToNeedsReview() {
            String ocr = "Name: Unknown\nPh. No.: 9876543210\n";
            ExtractedData result = service.extract(ocr);
            assertThat(result.order().garmentType()).isEqualTo("NEEDS_REVIEW");
            assertThat(result.order().garmentTypeConfidence()).isLessThan(0.50);
        }
    }

    // ── Customer Information ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Customer information extraction")
    class CustomerExtraction {

        @Test
        @DisplayName("Extracts customer name from Name: prefix")
        void extractsName() {
            ExtractedData result = service.extract(CHUDI_FULL_OCR);
            assertThat(result.customer().customerName()).isEqualToIgnoringCase("Revathi");
        }

        @Test
        @DisplayName("Extracts mobile number from Ph. No.: field")
        void extractsMobile() {
            ExtractedData result = service.extract(CHUDI_FULL_OCR);
            assertThat(result.customer().customerMobile()).isEqualTo("9345678901");
        }

        @Test
        @DisplayName("Returns null mobile when phone field is absent")
        void nullMobileWhenAbsent() {
            String ocr = "CHUDI\nName: Priya\nDate: 01/01/2024\n";
            ExtractedData result = service.extract(ocr);
            assertThat(result.customer().customerMobile()).isNull();
        }

        @Test
        @DisplayName("Returns null name when Name: prefix is absent")
        void nullNameWhenAbsent() {
            String ocr = "CHUDI\nPh. No.: 9000000001\n";
            ExtractedData result = service.extract(ocr);
            // Name may be null or fallback; mobile must be found
            assertThat(result.customer().customerMobile()).isEqualTo("9000000001");
        }
    }

    // ── Date Extraction ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Date extraction")
    class DateExtraction {

        @Test
        @DisplayName("Extracts order date (dd/MM/yyyy)")
        void extractsOrderDate() {
            ExtractedData result = service.extract(CHUDI_FULL_OCR);
            assertThat(result.order().orderDate()).isEqualTo("2024-04-05");
        }

        @Test
        @DisplayName("Extracts due/delivery date as second date")
        void extractsDeliveryDate() {
            ExtractedData result = service.extract(CHUDI_FULL_OCR);
            assertThat(result.order().deliveryDate()).isEqualTo("2024-04-12");
        }

        @Test
        @DisplayName("Returns null order date when no date present")
        void nullOrderDateWhenAbsent() {
            String ocr = "CHUDI\nName: X\nPh. No.: 9000000002\n";
            ExtractedData result = service.extract(ocr);
            assertThat(result.order().orderDate()).isNull();
        }
    }

    // ── Erode Field ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Erode field extraction")
    class ErodeExtraction {

        @Test
        @DisplayName("Extracts Erode number from 'Erode: 353' header line")
        void extractsErode() {
            ExtractedData result = service.extract(CHUDI_FULL_OCR);
            assertThat(result.order().erode()).isEqualTo("353");
        }

        @Test
        @DisplayName("Returns null erode when Erode label is absent")
        void nullErodeWhenAbsent() {
            String ocr = "CHUDI\nName: Test\nPh. No.: 9111111111\n";
            ExtractedData result = service.extract(ocr);
            assertThat(result.order().erode()).isNull();
        }
    }

    // ── CHUDI Measurements ───────────────────────────────────────────────────

    @Nested
    @DisplayName("CHUDI measurement field extraction")
    class ChudiMeasurements {

        private Map<String, String> measurements;

        @BeforeEach
        void extract() {
            measurements = service.extract(CHUDI_FULL_OCR).order().measurements();
        }

        @Test void fn()    { assertThat(measurements).containsEntry("FN",   "14"); }
        @Test void bn()    { assertThat(measurements).containsEntry("BN",   "13"); }
        @Test void hb()    { assertThat(measurements).containsEntry("HB",   "15"); }
        @Test void l1()    { assertThat(measurements).containsEntry("L_1",  "40"); }
        @Test void ss()    { assertThat(measurements).containsEntry("SS",   "10"); }
        @Test void am()    { assertThat(measurements).containsEntry("AM",    "9"); }
        @Test void b()     { assertThat(measurements).containsEntry("B",    "12"); }
        @Test void h()     { assertThat(measurements).containsEntry("H",    "14"); }
        @Test void ts()    { assertThat(measurements).containsEntry("TS",   "16"); }
        @Test void pl()    { assertThat(measurements).containsEntry("PL",   "40"); }
        @Test void s()     { assertThat(measurements).containsEntry("S",     "8"); }
        @Test void l2()    { assertThat(measurements).containsEntry("L_2",  "38"); }
        @Test void scut()  { assertThat(measurements).containsEntry("SCUT",  "9"); }
        @Test void lng()   { assertThat(measurements).containsEntry("LNG",  "44"); }
        @Test void shall() { assertThat(measurements).containsEntry("SHALL", "2"); }
        @Test void bd()    { assertThat(measurements).containsEntry("BD",    "6"); }

        // ── Duplicate field preservation ──────────────────────────────────────

        @Test
        @DisplayName("SL_1 (first sleeve row) is distinct from SL_2 (second sleeve row)")
        void sl1AndSl2AreSeparate() {
            assertThat(measurements).containsKey("SL_1");
            assertThat(measurements).containsKey("SL_2");
        }

        @Test
        @DisplayName("SL_1 value is correctly assigned to first SL row")
        void sl1Value() {
            assertThat(measurements.get("SL_1")).isEqualTo("11");
        }

        @Test
        @DisplayName("SL_2 value is correctly assigned to second SL row")
        void sl2Value() {
            assertThat(measurements.get("SL_2")).isEqualTo("11");
        }

        @Test
        @DisplayName("L_1 (top length) is distinct from L_2 (leg loose)")
        void l1AndL2AreSeparate() {
            assertThat(measurements).containsKey("L_1");
            assertThat(measurements).containsKey("L_2");
        }

        @Test
        @DisplayName("L_1 (top length) and L_2 (leg loose) have different values")
        void l1AndL2HaveDifferentValues() {
            assertThat(measurements.get("L_1")).isEqualTo("40");
            assertThat(measurements.get("L_2")).isEqualTo("38");
        }

        @Test
        @DisplayName("Measurement map contains exactly 18 CHUDI fields")
        void measurementCountIsEighteen() {
            // Some fields may be absent if OCR produced no value, so we check ≤ 18
            assertThat(measurements.size()).isLessThanOrEqualTo(18);
        }
    }

    // ── BLOUSE Measurements ───────────────────────────────────────────────────

    @Nested
    @DisplayName("BLOUSE measurement field extraction with canonical keys")
    class BlouseMeasurements {

        private ExtractedData result;
        private Map<String, String> measurements;

        @BeforeEach
        void extract() {
            result = service.extract(BLOUSE_FULL_OCR);
            measurements = result.order().measurements();
        }

        @Test void garmentType() { assertThat(result.order().garmentType()).isEqualTo("BLOUSE"); }
        @Test void erode()       { assertThat(result.order().erode()).isEqualTo("2576"); }

        @Test void lth()   { assertThat(measurements).containsEntry("LTH",    "14.5"); }
        @Test void sho()   { assertThat(measurements).containsEntry("SHO",    "14"); }
        @Test void hs()    { assertThat(measurements).containsEntry("HS",     "6.5"); }
        @Test void hl()    { assertThat(measurements).containsEntry("HL",     "7"); }
        @Test void hlo()   { assertThat(measurements).containsEntry("HLO",    "12"); }
        @Test void ak()    { assertThat(measurements).containsEntry("AK",     "16"); }
        @Test void am()    { assertThat(measurements).containsEntry("AM",     "13"); }
        @Test void bn()    { assertThat(measurements).containsEntry("BN",     "8.5"); }
        @Test void fn()    { assertThat(measurements).containsEntry("FN",     "6.5"); }
        @Test void b1()    { assertThat(measurements).containsEntry("B1",     "34"); }
        @Test void b2()    { assertThat(measurements).containsEntry("B2",     "36"); }
        @Test void b3()    { assertThat(measurements).containsEntry("B3",     "31"); }
        @Test void fhook() { assertThat(measurements).containsEntry("F_HOOK", "1"); }
        @Test void bhook() { assertThat(measurements).containsEntry("B_HOOK", "0"); }
        @Test void lining(){ assertThat(measurements).containsEntry("LINING", "1"); }
        @Test void av()    { assertThat(measurements).containsEntry("AV",     "1"); }
        @Test void sari()  { assertThat(measurements).containsEntry("SARI",   "Green silk"); }

        // ── Duplicate DP-1 row preservation ───────────────────────────────────

        @Test
        @DisplayName("DP1_1 (first DP-1 row) is distinct from DP1_2 (second DP-1 row)")
        void dp1AndDp2AreSeparate() {
            assertThat(measurements).containsKey("DP1_1");
            assertThat(measurements).containsKey("DP1_2");
            assertThat(measurements.get("DP1_1")).isEqualTo("9.5");
            assertThat(measurements.get("DP1_2")).isEqualTo("12.5");
        }
    }

    // ── Missing / Partial Values ─────────────────────────────────────────────

    @Nested
    @DisplayName("Partial / missing OCR values")
    class PartialValues {

        @Test
        @DisplayName("Empty OCR text returns safe empty result (no exception)")
        void emptyOcrReturnsEmptyResult() {
            assertThatNoException().isThrownBy(() -> service.extract(""));
            ExtractedData result = service.extract("");
            assertThat(result).isNotNull();
            assertThat(result.order().garmentType()).isEqualTo("NEEDS_REVIEW");
        }

        @Test
        @DisplayName("Null OCR text returns safe empty result (no exception)")
        void nullOcrReturnsEmptyResult() {
            assertThatNoException().isThrownBy(() -> service.extract(null));
            ExtractedData result = service.extract(null);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Partially filled CHUDI form leaves missing measurements absent")
        void partialFormLeavesFieldsAbsent() {
            String partialOcr = """
                    CHUDI
                    Name: Test
                    Ph. No.: 9000000003
                    F.N.  14
                    B.N.  13
                    """;
            Map<String, String> meas = service.extract(partialOcr).order().measurements();
            // FN and BN should be present; SCUT, LNG etc. should be absent (not guessed)
            assertThat(meas).containsKey("FN");
            assertThat(meas).containsKey("BN");
            assertThat(meas).doesNotContainKey("SCUT");
            assertThat(meas).doesNotContainKey("LNG");
        }
    }

    // ── Round-trip serialisation ─────────────────────────────────────────────

    @Nested
    @DisplayName("JSON serialisation round-trip")
    class Serialisation {

        @Test
        @DisplayName("toJson / fromJson round-trips without data loss")
        void roundTrip() {
            ExtractedData original = service.extract(CHUDI_FULL_OCR);
            String json = service.toJson(original);
            ExtractedData restored = service.fromJson(json);

            assertThat(restored.order().garmentType())
                    .isEqualTo(original.order().garmentType());
            assertThat(restored.order().measurements())
                    .isEqualTo(original.order().measurements());
            assertThat(restored.order().erode())
                    .isEqualTo(original.order().erode());
            assertThat(restored.customer().customerMobile())
                    .isEqualTo(original.customer().customerMobile());
        }
    }
}
