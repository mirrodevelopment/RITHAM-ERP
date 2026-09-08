package com.ritham.erp.module.branch.dto;

import com.ritham.erp.module.branch.entity.Branch;
import org.springframework.stereotype.Component;

@Component
public class BranchMapper {

    public BranchResponse toResponse(Branch branch, Long employeeCount, Long orderCount) {
        if (branch == null) return null;
        return BranchResponse.builder()
                .id(branch.getId())
                .branchCode(branch.getBranchCode())
                .name(branch.getName())
                .city(branch.getCity())
                .address(branch.getAddress())
                .phone(branch.getPhone())
                .email(branch.getEmail())
                .gstNumber(branch.getGstNumber())
                .isActive(branch.getIsActive())
                .employeeCount(employeeCount != null ? employeeCount : 0L)
                .orderCount(orderCount != null ? orderCount : 0L)
                .createdAt(branch.getCreatedAt())
                .build();
    }

    public BranchResponse toResponse(Branch branch) {
        return toResponse(branch, 0L, 0L);
    }
}
