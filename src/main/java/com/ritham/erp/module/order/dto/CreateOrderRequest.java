package com.ritham.erp.module.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class CreateOrderRequest {

    @NotBlank(message = "Customer mobile number is required")
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Please enter a valid 10-digit Indian mobile number")
    private String customerMobile;

    @NotNull(message = "Delivery date is required")
    private LocalDateTime deliveryDate;

    @NotNull(message = "Total amount is required")
    @DecimalMin(value = "0.0", message = "Total amount must be non-negative")
    private BigDecimal totalAmount;

    @DecimalMin(value = "0.0", message = "Advance amount must be non-negative")
    private BigDecimal advanceAmount;

    @DecimalMin(value = "0.0", message = "Discount amount must be non-negative")
    private BigDecimal discountAmount;

    private String paymentMode;

    private String garmentType;

    private String lining;

    private Long branchId;

    private java.util.Map<String, String> measurements;
}
