package com.ritham.erp.module.production.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductionStageResponse {
    private Long id;
    private String stageKey;
    private String title;
    private String description;
    private String icon;
    private Integer seqOrder;
    private String color;
    private String bgColor;
    private Boolean isActive;
}
