package com.ritham.erp.module.employee.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RoleResponse {
    private Long id;
    private String name;
    private String description;
}
