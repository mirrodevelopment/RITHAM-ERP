package com.ritham.erp.module.migration.service.form;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Central registry of all {@link MeasurementFormDefinition} implementations.
 *
 * <p>Inject this registry wherever a form definition is needed.
 * Spring auto-discovers all beans implementing {@link MeasurementFormDefinition}
 * so adding a new garment type only requires creating a new {@code @Component} class.
 */
@Component
public class FormDefinitionRegistry {

    private final Map<String, MeasurementFormDefinition> byGarmentType;

    public FormDefinitionRegistry(List<MeasurementFormDefinition> definitions) {
        this.byGarmentType = definitions.stream()
                .collect(Collectors.toMap(
                        d -> d.garmentType().toUpperCase(),
                        Function.identity()
                ));
    }

    /**
     * Look up the form definition for the given garment type string.
     *
     * @param garmentType e.g. "BLOUSE", "CHUDI", "CHURIDAR"
     * @return the matching definition, or empty if not registered
     */
    public Optional<MeasurementFormDefinition> find(String garmentType) {
        if (garmentType == null) return Optional.empty();
        String key = garmentType.toUpperCase();
        // CHURIDAR is handled by CHUDI definition
        if ("CHURIDAR".equals(key)) key = "CHUDI";
        return Optional.ofNullable(byGarmentType.get(key));
    }

    /**
     * Returns all registered definitions (for garment-type inference).
     */
    public List<MeasurementFormDefinition> all() {
        return List.copyOf(byGarmentType.values());
    }
}
