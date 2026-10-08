package com.ritham.erp.module.production.controller;

import com.ritham.erp.common.response.ApiResponse;
import com.ritham.erp.module.production.dto.*;
import com.ritham.erp.module.production.service.ProductionStageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/production-stages")
@RequiredArgsConstructor
public class ProductionStageController {

    private final ProductionStageService stageService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductionStageResponse>>> getAllStages(
            @RequestParam(defaultValue = "true") boolean activeOnly
    ) {
        List<ProductionStageResponse> stages = activeOnly
                ? stageService.getAllActiveStages()
                : stageService.getAllStages();
        return ResponseEntity.ok(ApiResponse.success(stages));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATIONS_MANAGER')")
    public ResponseEntity<ApiResponse<ProductionStageResponse>> createStage(
            @Valid @RequestBody CreateProductionStageRequest request
    ) {
        ProductionStageResponse created = stageService.createCustomStage(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Production stage created successfully", created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATIONS_MANAGER')")
    public ResponseEntity<ApiResponse<ProductionStageResponse>> updateStage(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductionStageRequest request
    ) {
        ProductionStageResponse updated = stageService.updateStage(id, request);
        return ResponseEntity.ok(ApiResponse.success("Production stage updated successfully", updated));
    }

    @PutMapping("/reorder")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATIONS_MANAGER')")
    public ResponseEntity<ApiResponse<List<ProductionStageResponse>>> reorderStages(
            @Valid @RequestBody ReorderStagesRequest request
    ) {
        List<ProductionStageResponse> reordered = stageService.reorderStages(request);
        return ResponseEntity.ok(ApiResponse.success("Production stages reordered successfully", reordered));
    }

    @DeleteMapping("/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATIONS_MANAGER')")
    public ResponseEntity<ApiResponse<Void>> deleteAllStages() {
        stageService.deleteAllStages();
        return ResponseEntity.ok(ApiResponse.success("All production stages removed from database"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATIONS_MANAGER')")
    public ResponseEntity<ApiResponse<Void>> deleteStage(@PathVariable Long id) {
        stageService.deleteStage(id);
        return ResponseEntity.ok(ApiResponse.success("Production stage deactivated successfully"));
    }
}
