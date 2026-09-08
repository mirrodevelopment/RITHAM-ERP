package com.ritham.erp.module.production.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateProductionStageRequest {

    @NotBlank(message = "Stage title is required")
    @Size(max = 100, message = "Stage title must not exceed 100 characters")
    private String title;

    @Size(max = 255, message = "Description must not exceed 255 characters")
    private String description;

    private String icon;
    private String color;
    private String bgColor;
    private Boolean isActive;
}
