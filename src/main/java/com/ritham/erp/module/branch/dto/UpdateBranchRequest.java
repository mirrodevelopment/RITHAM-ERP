package com.ritham.erp.module.branch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateBranchRequest {

    @NotBlank(message = "Branch name is required")
    @Size(max = 100, message = "Branch name cannot exceed 100 characters")
    private String name;

    @Size(max = 50, message = "City cannot exceed 50 characters")
    private String city;

    @Size(max = 255, message = "Address cannot exceed 255 characters")
    private String address;

    @Size(max = 20, message = "Phone cannot exceed 20 characters")
    private String phone;

    @Size(max = 100, message = "Email cannot exceed 100 characters")
    private String email;

    @Size(max = 30, message = "GST number cannot exceed 30 characters")
    private String gstNumber;

    private Boolean isActive;
}
