package com.ritham.erp.module.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class SaveCustomerMeasurementRequest {

    @NotBlank(message = "Customer mobile number is required")
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Please enter a valid 10-digit Indian mobile number")
    private String customerMobile;

    @NotBlank(message = "Customer name is required")
    @Size(max = 100, message = "Customer name must not exceed 100 characters")
    private String customerName;

    @NotBlank(message = "Garment type is required (e.g. BLOUSE, CHUDI)")
    private String garmentType;

    private String lining;

    private Map<String, String> measurements;

    private String notes;
}
