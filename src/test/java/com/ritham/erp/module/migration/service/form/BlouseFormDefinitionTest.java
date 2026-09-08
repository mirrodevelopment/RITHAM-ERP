package com.ritham.erp.module.migration.service.form;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BlouseFormDefinition}.
 */
class BlouseFormDefinitionTest {

    private BlouseFormDefinition def;

    @BeforeEach
    void setUp() {
        def = new BlouseFormDefinition();
    }

    @Test
    @DisplayName("Garment type is BLOUSE")
    void garmentTypeIsBlouse() {
        assertThat(def.garmentType()).isEqualTo("BLOUSE");
    }

    @Test
    @DisplayName("Canonical keys contain exactly 19 spec fields")
    void canonicalKeysCount() {
        List<String> keys = def.canonicalKeys();
        assertThat(keys).containsExactly(
                "LTH", "SHO", "HS", "HL", "HLO",
                "AK", "AM", "BN", "FN",
                "DP1_1", "DP1_2",
                "B1", "B2", "B3",
                "F_HOOK", "B_HOOK",
                "LINING", "AV", "SARI"
        );
    }

    @Test
    @DisplayName("Resolves raw aliases to canonical keys")
    void resolvesAliases() {
        assertThat(def.resolveKey("H.S.")).isEqualTo("HS");
        assertThat(def.resolveKey("H.L.")).isEqualTo("HL");
        assertThat(def.resolveKey("H.LO")).isEqualTo("HLO");
        assertThat(def.resolveKey("B-1")).isEqualTo("B1");
        assertThat(def.resolveKey("B-2")).isEqualTo("B2");
        assertThat(def.resolveKey("B-3")).isEqualTo("B3");
        assertThat(def.resolveKey("F.HOOK")).isEqualTo("F_HOOK");
        assertThat(def.resolveKey("B.HOOK")).isEqualTo("B_HOOK");
        assertThat(def.resolveKey("AV.")).isEqualTo("AV");
    }

    @Test
    @DisplayName("Extracts all measurements and separates duplicate DP-1 rows")
    void extractsMeasurementsAndSeparatesDp1() {
        String[] lines = {
                "LTH 14.5",
                "SHO 14",
                "H.S. 6.5",
                "H.L. 7",
                "H.LO 12",
                "AK 16",
                "AM 13",
                "BN 8.5",
                "FN 6.5",
                "DP-1 9.5",
                "DP-1 12.5",
                "B-1 34",
                "B-2 36",
                "B-3 31",
                "F.HOOK 1",
                "B.HOOK 0",
                "LINING 1",
                "AV. 1",
                "SARI Silk"
        };

        Map<String, String> meas = def.extractMeasurements(lines, Collections.emptyList());

        assertThat(meas).containsEntry("LTH", "14.5");
        assertThat(meas).containsEntry("SHO", "14");
        assertThat(meas).containsEntry("HS", "6.5");
        assertThat(meas).containsEntry("HL", "7");
        assertThat(meas).containsEntry("HLO", "12");
        assertThat(meas).containsEntry("AK", "16");
        assertThat(meas).containsEntry("AM", "13");
        assertThat(meas).containsEntry("BN", "8.5");
        assertThat(meas).containsEntry("FN", "6.5");
        assertThat(meas).containsEntry("DP1_1", "9.5");
        assertThat(meas).containsEntry("DP1_2", "12.5");
        assertThat(meas).containsEntry("B1", "34");
        assertThat(meas).containsEntry("B2", "36");
        assertThat(meas).containsEntry("B3", "31");
        assertThat(meas).containsEntry("F_HOOK", "1");
        assertThat(meas).containsEntry("B_HOOK", "0");
        assertThat(meas).containsEntry("LINING", "1");
        assertThat(meas).containsEntry("AV", "1");
        assertThat(meas).containsEntry("SARI", "Silk");
    }
}
