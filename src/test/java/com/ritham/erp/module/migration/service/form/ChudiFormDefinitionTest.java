package com.ritham.erp.module.migration.service.form;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ChudiFormDefinition}.
 */
class ChudiFormDefinitionTest {

    private ChudiFormDefinition def;

    @BeforeEach
    void setUp() {
        def = new ChudiFormDefinition();
    }

    @Test
    @DisplayName("Garment type is CHUDI")
    void garmentTypeIsChudi() {
        assertThat(def.garmentType()).isEqualTo("CHUDI");
    }

    @Test
    @DisplayName("Canonical keys contain exactly 18 spec fields")
    void canonicalKeysCount() {
        List<String> keys = def.canonicalKeys();
        assertThat(keys).containsExactly(
                "FN", "BN", "HB", "L_1", "SS", "SL_1", "SL_2",
                "AM", "B", "H", "TS", "PL", "S", "L_2",
                "SCUT", "LNG", "SHALL", "BD"
        );
    }

    @Test
    @DisplayName("Extracts all measurements and separates duplicate SL and L rows")
    void extractsMeasurementsAndSeparatesDuplicates() {
        String[] lines = {
                "F.N. 14",
                "B.N. 13",
                "H.B. 15",
                "L 40",
                "SS 10",
                "SL 11",
                "SL 11",
                "AM 9",
                "B 12",
                "H 14",
                "T.S. 16",
                "PL. 40",
                "S. 8",
                "L. 38",
                "S.CUT 9",
                "LNG 44",
                "SHALL 2",
                "B.D. 6"
        };

        Map<String, String> meas = def.extractMeasurements(lines, Collections.emptyList());

        assertThat(meas).containsEntry("FN", "14");
        assertThat(meas).containsEntry("BN", "13");
        assertThat(meas).containsEntry("HB", "15");
        assertThat(meas).containsEntry("L_1", "40");
        assertThat(meas).containsEntry("SS", "10");
        assertThat(meas).containsEntry("SL_1", "11");
        assertThat(meas).containsEntry("SL_2", "11");
        assertThat(meas).containsEntry("AM", "9");
        assertThat(meas).containsEntry("B", "12");
        assertThat(meas).containsEntry("H", "14");
        assertThat(meas).containsEntry("TS", "16");
        assertThat(meas).containsEntry("PL", "40");
        assertThat(meas).containsEntry("S", "8");
        assertThat(meas).containsEntry("L_2", "38");
        assertThat(meas).containsEntry("SCUT", "9");
        assertThat(meas).containsEntry("LNG", "44");
        assertThat(meas).containsEntry("SHALL", "2");
        assertThat(meas).containsEntry("BD", "6");
    }
}
