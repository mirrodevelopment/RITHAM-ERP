package com.ritham.erp.module.migration.dto;


/** Request body for creating a new migration batch. */
public record CreateBatchRequest(

        Long branchId,

        Short sourceYear,

        String description
) {}

