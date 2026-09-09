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
                "LTH", "SHO", "H.S.", "H.L.", "H.LO",
                "AK", "AM", "BN", "FN",
                "DP-1-1", "DP-1-2",
                "B-1", "B-2", "B-3",
                "F-HOOK", "B-HOOK",
                "LINING", "AV.", "SARI"
        );
    }

    @Test
    @DisplayName("Resolves raw aliases to canonical keys")
    void resolvesAliases() {
        assertThat(def.resolveKey("HS")).isEqualTo("H.S.");
        assertThat(def.resolveKey("HL")).isEqualTo("H.L.");
        assertThat(def.resolveKey("HLO")).isEqualTo("H.LO");
        assertThat(def.resolveKey("B1")).isEqualTo("B-1");
        assertThat(def.resolveKey("B2")).isEqualTo("B-2");
        assertThat(def.resolveKey("B3")).isEqualTo("B-3");
        assertThat(def.resolveKey("F_HOOK")).isEqualTo("F-HOOK");
        assertThat(def.resolveKey("B_HOOK")).isEqualTo("B-HOOK");
        assertThat(def.resolveKey("AV")).isEqualTo("AV.");
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
        assertThat(meas).containsEntry("H.S.", "6.5");
        assertThat(meas).containsEntry("H.L.", "7");
        assertThat(meas).containsEntry("H.LO", "12");
        assertThat(meas).containsEntry("AK", "16");
        assertThat(meas).containsEntry("AM", "13");
        assertThat(meas).containsEntry("BN", "8.5");
        assertThat(meas).containsEntry("FN", "6.5");
        assertThat(meas).containsEntry("DP-1-1", "9.5");
        assertThat(meas).containsEntry("DP-1-2", "12.5");
        assertThat(meas).containsEntry("B-1", "34");
        assertThat(meas).containsEntry("B-2", "36");
        assertThat(meas).containsEntry("B-3", "31");
        assertThat(meas).containsEntry("F-HOOK", "1");
        assertThat(meas).containsEntry("B-HOOK", "0");
        assertThat(meas).containsEntry("LINING", "1");
        assertThat(meas).containsEntry("AV.", "1");
        assertThat(meas).containsEntry("SARI", "Silk");
    }
}
