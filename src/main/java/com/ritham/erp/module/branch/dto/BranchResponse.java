package com.ritham.erp.module.branch.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BranchResponse {
    private Long id;
    private String branchCode;
    private String name;
    private String city;
    private String address;
    private String phone;
    private String email;
    private String gstNumber;
    private Boolean isActive;
    private Long employeeCount;
    private Long orderCount;
    private LocalDateTime createdAt;
}
