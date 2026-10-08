package com.ritham.erp.module.production.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateProductionStageRequest {

    @NotBlank(message = "Stage title is required")
    @Size(max = 100, message = "Stage title must not exceed 100 characters")
    private String title;

    @Size(max = 255, message = "Description must not exceed 255 characters")
    private String description;

    @Size(max = 50, message = "Stage key must not exceed 50 characters")
    private String stageKey;

    private String icon;
    private String color;
    private String bgColor;
}
