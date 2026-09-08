package com.ritham.erp.module.customer.controller;

import com.ritham.erp.common.response.ApiResponse;
import com.ritham.erp.common.response.PageResponse;
import com.ritham.erp.module.customer.dto.CreateCustomerRequest;
import com.ritham.erp.module.customer.dto.CustomerResponse;
import com.ritham.erp.module.customer.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<CustomerResponse>>> getCustomers(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        Sort sort = sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        Pageable pageable = PageRequest.of(page, size, sort);
        PageResponse<CustomerResponse> response = customerService.getCustomers(search, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{mobile}")
    public ResponseEntity<ApiResponse<CustomerResponse>> getCustomerByMobile(@PathVariable String mobile) {
        CustomerResponse customer = customerService.getCustomerByMobile(mobile);
        return ResponseEntity.ok(ApiResponse.success(customer));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CustomerResponse>> createCustomer(
            @Valid @RequestBody CreateCustomerRequest request
    ) {
        CustomerResponse created = customerService.createCustomer(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Customer created successfully", created));
    }

    @PutMapping("/{mobile}")
    public ResponseEntity<ApiResponse<CustomerResponse>> updateCustomer(
            @PathVariable String mobile,
            @Valid @RequestBody com.ritham.erp.module.customer.dto.UpdateCustomerRequest request
    ) {
        CustomerResponse updated = customerService.updateCustomer(mobile, request);
        return ResponseEntity.ok(ApiResponse.success("Customer updated successfully", updated));
    }

    @DeleteMapping("/{mobile}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCustomer(@PathVariable String mobile) {
        customerService.deleteCustomer(mobile);
        return ResponseEntity.ok(ApiResponse.success("Customer deleted successfully", null));
    }
}
