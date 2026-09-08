package com.ritham.erp.module.customer.controller;

import com.ritham.erp.common.response.ApiResponse;
import com.ritham.erp.common.response.PageResponse;
import com.ritham.erp.module.customer.dto.CustomerMeasurementResponse;
import com.ritham.erp.module.customer.dto.SaveCustomerMeasurementRequest;
import com.ritham.erp.module.customer.service.CustomerMeasurementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customer-measurements")
@RequiredArgsConstructor
public class CustomerMeasurementController {

    private final CustomerMeasurementService measurementService;

    @PostMapping
    public ResponseEntity<ApiResponse<CustomerMeasurementResponse>> saveOrUpdate(
            @Valid @RequestBody SaveCustomerMeasurementRequest request
    ) {
        CustomerMeasurementResponse response = measurementService.saveOrUpdate(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Customer measurements saved successfully", response));
    }

    @GetMapping("/customer/{mobile}")
    public ResponseEntity<ApiResponse<List<CustomerMeasurementResponse>>> getByCustomerMobile(
            @PathVariable String mobile
    ) {
        List<CustomerMeasurementResponse> list = measurementService.getByCustomerMobile(mobile);
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @GetMapping("/customer/{mobile}/latest")
    public ResponseEntity<ApiResponse<CustomerMeasurementResponse>> getLatestByCustomerMobile(
            @PathVariable String mobile
    ) {
        CustomerMeasurementResponse response = measurementService.getLatestByCustomerMobile(mobile).orElse(null);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/customer/{mobile}/garment/{garmentType}")
    public ResponseEntity<ApiResponse<CustomerMeasurementResponse>> getByGarment(
            @PathVariable String mobile,
            @PathVariable String garmentType
    ) {
        CustomerMeasurementResponse response = measurementService.getByCustomerMobileAndGarment(mobile, garmentType).orElse(null);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<CustomerMeasurementResponse>>> getAllMeasurements(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        Sort sort = sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        Pageable pageable = PageRequest.of(page, size, sort);
        PageResponse<CustomerMeasurementResponse> response = measurementService.getAllMeasurements(search, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATIONS_MANAGER')")
    public ResponseEntity<ApiResponse<Void>> deleteMeasurement(@PathVariable Long id) {
        measurementService.deleteMeasurement(id);
        return ResponseEntity.ok(ApiResponse.success("Customer measurement deleted successfully", null));
    }
}
