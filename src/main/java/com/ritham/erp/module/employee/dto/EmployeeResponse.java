package com.ritham.erp.module.employee.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class EmployeeResponse {
    private Long id;
    private String employeeCode;
    private String fullName;
    private String mobileNumber;
    private String email;
    private String username;
    private Long roleId;
    private String role;
    private String roleLabel;
    private Long departmentId;
    private String department;
    private Long branchId;
    private String branchName;
    private String branchCode;
    private String stage;
    private Integer advance;
    private LocalDate joiningDate;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
