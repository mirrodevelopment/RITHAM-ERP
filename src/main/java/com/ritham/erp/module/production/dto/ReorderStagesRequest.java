package com.ritham.erp.module.production.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ReorderStagesRequest {

    @NotEmpty(message = "Stage IDs list cannot be empty")
    private List<Long> stageIds;
}
