package com.ritham.erp.module.migration.service.ocr;

import com.ritham.erp.module.migration.dto.ExtractedData;
import com.ritham.erp.module.migration.service.OcrService.WordBox;
import com.ritham.erp.module.migration.service.form.FormDefinitionRegistry;
import com.ritham.erp.module.migration.service.form.MeasurementFormDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.util.*;

/**
 * Extracts measurement fields by identifying printed labels in the left measurement column
 * and reading handwritten numeric values from the cell box immediately to the right.
 * Handles duplicate physical labels ordinally (DP-1-1/DP-1-2, SL-1/SL-2, L/L-2).
 */
@Component
@Slf4j
public class MeasurementExtractionService {

    public record MeasurementField(
            String canonicalKey,
            int index,
            String value,
            Double confidence,
            ExtractedData.RegionBox boundingBox
    ) {}

    public record MeasurementResult(
            Map<String, String> measurements,
            Map<String, Double> confidences,
            Map<String, ExtractedData.RegionBox> regions
    ) {}

    private final FormDefinitionRegistry formRegistry;

    public MeasurementExtractionService(FormDefinitionRegistry formRegistry) {
        this.formRegistry = formRegistry;
    }

    /**
     * Extracts measurements from the measurement column crop.
     */
    public MeasurementResult extract(
            BufferedImage measCrop,
            RegionExtractor.NormalizedRect measBounds,
            String garmentType,
            OcrEngine ocrEngine) {

        Map<String, String> values = new LinkedHashMap<>();
        Map<String, Double> confs = new LinkedHashMap<>();
        Map<String, ExtractedData.RegionBox> regions = new LinkedHashMap<>();

        if (measCrop == null) {
            return new MeasurementResult(values, confs, regions);
        }

        // 1. OCR measurement crop (PSM 6 or PSM 4: single column of variable text)
        OcrEngine.OcrEngineResult ocrRes = ocrEngine.ocrRegion(
                measCrop, 0.0, 0.0, 1.0, 1.0, "eng", net.sourceforge.tess4j.ITessAPI.TessPageSegMode.PSM_SINGLE_COLUMN);

        List<WordBox> cropBoxes = ocrRes.wordBoxes();
        String cropText = ocrRes.text();
        String[] cropLines = cropText != null ? cropText.split("\\r?\\n") : new String[0];

        // 2. Delegate to active form definition (BLOUSE or CHUDI)
        Optional<MeasurementFormDefinition> formOpt = formRegistry.find(garmentType);
        if (formOpt.isEmpty() && "NEEDS_REVIEW".equals(garmentType)) {
            // Evaluate both and take the one with higher match count
            var blouseOpt = formRegistry.find("BLOUSE");
            var chudiOpt = formRegistry.find("CHUDI");
            Map<String, String> bMeas = blouseOpt.map(d -> d.extractMeasurements(cropLines, cropBoxes)).orElse(Collections.emptyMap());
            Map<String, String> cMeas = chudiOpt.map(d -> d.extractMeasurements(cropLines, cropBoxes)).orElse(Collections.emptyMap());
            if (bMeas.size() >= cMeas.size()) {
                formOpt = blouseOpt;
                values.putAll(bMeas);
            } else {
                formOpt = chudiOpt;
                values.putAll(cMeas);
            }
        } else if (formOpt.isPresent()) {
            values.putAll(formOpt.get().extractMeasurements(cropLines, cropBoxes));
        }

        // 3. For each extracted measurement, associate its spatial bounding box and confidence
        MeasurementFormDefinition formDef = formOpt.orElse(null);
        List<String> canonicalKeys = formDef != null ? formDef.canonicalKeys() : new ArrayList<>(values.keySet());

        for (String key : canonicalKeys) {
            String val = values.get(key);
            if (val != null && !val.isBlank()) {
                // High confidence for valid numeric measurement
                double c = 0.94;
                confs.put(key, c);
            } else {
                // Unread or missing
                confs.put(key, 0.0);
            }
        }

        // 4. Create micro-regions for visual highlighting on original image
        for (WordBox b : cropBoxes) {
            String rawT = b.text().toUpperCase().replaceAll("[^A-Z0-9.\\-]", "");
            if (rawT.isBlank()) continue;

            for (String key : canonicalKeys) {
                String cleanKey = key.toUpperCase().replaceAll("[^A-Z0-9]", "");
                if (cleanKey.isEmpty()) continue;

                if (rawT.equals(cleanKey) || (rawT.length() >= 3 && cleanKey.contains(rawT))) {
                    double gx = measBounds.x() + (b.x() * measBounds.width());
                    double gy = measBounds.y() + (b.y() * measBounds.height());
                    double gw = b.width() * measBounds.width();
                    double gh = b.height() * measBounds.height();

                    // Expand slightly to cover adjacent handwritten value cell
                    gw = Math.min(0.28, gw * 3.5);

                    String regKey = "meas_" + key;
                    if (!regions.containsKey(regKey)) {
                        regions.put(regKey, new ExtractedData.RegionBox(
                                Math.round(gx * 1000.0) / 1000.0,
                                Math.round(gy * 1000.0) / 1000.0,
                                Math.round(gw * 1000.0) / 1000.0,
                                Math.round(gh * 1000.0) / 1000.0,
                                "#ea580c",
                                key,
                                List.of(regKey)
                        ));
                    }
                    break;
                }
            }
        }

        // Add parent measurement region
        regions.put("measurementRegion", new ExtractedData.RegionBox(
                measBounds.x(), measBounds.y(), measBounds.width(), measBounds.height(),
                "#ea580c", "Measurements", Collections.emptyList()
        ));

        return new MeasurementResult(values, confs, regions);
    }
}
