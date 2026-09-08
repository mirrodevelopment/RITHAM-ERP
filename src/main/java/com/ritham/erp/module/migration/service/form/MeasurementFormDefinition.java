package com.ritham.erp.module.migration.service.form;

import com.ritham.erp.module.migration.service.OcrService;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Contract for a garment-specific measurement form definition.
 *
 * <p>Each implementation encapsulates:
 * <ul>
 *   <li>The canonical JSON key list (in display order)</li>
 *   <li>OCR-label → canonical-key alias map</li>
 *   <li>Marker patterns used to infer the garment type when the header keyword is absent</li>
 *   <li>A position-aware measurement extractor that preserves duplicate rows</li>
 * </ul>
 *
 * <p>To add a new garment form (e.g. SALWAR):
 * <ol>
 *   <li>Create {@code SalwarFormDefinition implements MeasurementFormDefinition}</li>
 *   <li>Register it in {@link FormDefinitionRegistry}</li>
 * </ol>
 */
public interface MeasurementFormDefinition {

    /**
     * The canonical garment type string stored in the database, e.g. "BLOUSE" or "CHUDI".
     */
    String garmentType();

    /**
     * Ordered list of canonical internal JSON keys for this garment's measurement fields.
     * The display order matches the physical printed form top-to-bottom (or left-to-right
     * when read in portrait orientation).
     *
     * <p>Duplicate printed labels are represented as separate keys:
     * BLOUSE: DP1_1, DP1_2 (two rows both printed "DP-1")
     * CHUDI:  SL_1, SL_2  (two rows both printed "SL")
     *         L_1,  L_2   (two rows both printed "L" and "L.")
     */
    List<String> canonicalKeys();

    /**
     * Flat alias map: raw OCR label (uppercase, stripped of punctuation) → canonical key.
     * Used to normalize OCR output variants to stable JSON keys.
     */
    Map<String, String> aliases();

    /**
     * Patterns whose presence in raw OCR text strongly suggests this garment type.
     * Used by {@link com.ritham.erp.module.migration.service.ExtractionService}
     * when the explicit garment header keyword is not legible.
     */
    List<Pattern> markerPatterns();

    /**
     * Position-aware measurement extractor.
     *
     * <p>Processes OCR lines in order of appearance, matching each line against
     * {@link #aliases()} and handling duplicate-label rows by position (first occurrence
     * of "SL" → SL_1, second → SL_2).
     *
     * @param lines     raw OCR text split into lines
     * @param wordBoxes word bounding boxes (used for confidence weighting; may be empty)
     * @return ordered map of canonical-key → value string (never null; may be empty)
     */
    Map<String, String> extractMeasurements(String[] lines, List<OcrService.WordBox> wordBoxes);
}
