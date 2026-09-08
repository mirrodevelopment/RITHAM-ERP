package com.ritham.erp.module.employee.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DepartmentResponse {
    private Long id;
    private String name;
}
