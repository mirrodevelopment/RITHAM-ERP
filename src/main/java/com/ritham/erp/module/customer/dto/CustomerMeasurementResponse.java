package com.ritham.erp.module.customer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerMeasurementResponse {

    private Long id;
    private String customerMobile;
    private String customerName;
    private String garmentType;
    private String lining;
    private Map<String, String> measurements;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
