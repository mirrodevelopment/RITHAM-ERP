package com.ritham.erp.module.branch.controller;

import com.ritham.erp.common.response.ApiResponse;
import com.ritham.erp.module.branch.dto.*;
import com.ritham.erp.module.branch.service.BranchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchService branchService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<BranchResponse>>> getAllBranches(
            @RequestParam(defaultValue = "false") boolean activeOnly
    ) {
        List<BranchResponse> list = branchService.getAllBranches(activeOnly);
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BranchResponse>> getBranchById(@PathVariable Long id) {
        BranchResponse branch = branchService.getBranchById(id);
        return ResponseEntity.ok(ApiResponse.success(branch));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BranchResponse>> createBranch(
            @Valid @RequestBody CreateBranchRequest request
    ) {
        BranchResponse created = branchService.createBranch(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Branch created successfully", created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BranchResponse>> updateBranch(
            @PathVariable Long id,
            @Valid @RequestBody UpdateBranchRequest request
    ) {
        BranchResponse updated = branchService.updateBranch(id, request);
        return ResponseEntity.ok(ApiResponse.success("Branch updated successfully", updated));
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> toggleStatus(@PathVariable Long id) {
        branchService.toggleBranchStatus(id);
        return ResponseEntity.ok(ApiResponse.success("Branch status updated successfully"));
    }
}
