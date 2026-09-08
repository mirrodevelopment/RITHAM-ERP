package com.ritham.erp.module.customer.service;

import com.ritham.erp.common.exception.AppException;
import com.ritham.erp.common.exception.ErrorCode;
import com.ritham.erp.common.response.PageResponse;
import com.ritham.erp.module.customer.dto.CreateCustomerRequest;
import com.ritham.erp.module.customer.dto.CustomerMapper;
import com.ritham.erp.module.customer.dto.CustomerResponse;
import com.ritham.erp.module.customer.entity.Customer;
import com.ritham.erp.module.customer.repository.CustomerMeasurementRepository;
import com.ritham.erp.module.customer.repository.CustomerRepository;
import com.ritham.erp.module.customer.dto.UpdateCustomerRequest;
import com.ritham.erp.module.order.repository.CustomerOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final CustomerMeasurementRepository customerMeasurementRepository;
    private final CustomerMapper customerMapper;

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> getCustomers(String search, Pageable pageable) {
        Page<Customer> page;
        if (search != null && !search.trim().isEmpty()) {
            page = customerRepository.search(search.trim(), pageable);
        } else {
            page = customerRepository.findAll(pageable);
        }
        Page<CustomerResponse> dtoPage = page.map(customerMapper::toResponse);
        return PageResponse.of(dtoPage);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomerByMobile(String mobile) {
        Customer customer = customerRepository.findByCustomerMobile(mobile.trim())
                .orElseThrow(() -> new AppException(ErrorCode.CUSTOMER_NOT_FOUND));
        return customerMapper.toResponse(customer);
    }

    @Transactional
    public CustomerResponse createCustomer(CreateCustomerRequest request) {
        String cleanMobile = request.getCustomerMobile().trim();
        if (customerRepository.existsByCustomerMobile(cleanMobile)) {
            throw new AppException(ErrorCode.CUSTOMER_ALREADY_EXISTS);
        }

        Customer customer = Customer.builder()
                .customerMobile(cleanMobile)
                .customerName(request.getCustomerName().trim())
                .build();

        Customer saved = customerRepository.save(customer);
        return customerMapper.toResponse(saved);
    }

    @Transactional
    public CustomerResponse updateCustomer(String mobile, UpdateCustomerRequest request) {
        Customer customer = customerRepository.findByCustomerMobile(mobile.trim())
                .orElseThrow(() -> new AppException(ErrorCode.CUSTOMER_NOT_FOUND));

        customer.setCustomerName(request.getCustomerName().trim());
        Customer saved = customerRepository.save(customer);
        return customerMapper.toResponse(saved);
    }

    @Transactional
    public void deleteCustomer(String mobile) {
        String cleanMobile = mobile != null ? mobile.trim() : "";
        Customer customer = customerRepository.findByCustomerMobile(cleanMobile)
                .orElseThrow(() -> new AppException(ErrorCode.CUSTOMER_NOT_FOUND));

        if (customerOrderRepository.existsByCustomerMobile(cleanMobile)
                || (customer.getTotalOrders() != null && customer.getTotalOrders() > 0)) {
            throw new AppException(ErrorCode.OPERATION_NOT_ALLOWED, "Cannot delete customer because they have existing order history");
        }

        if (customerMeasurementRepository.existsByCustomerMobile(cleanMobile)) {
            throw new AppException(ErrorCode.OPERATION_NOT_ALLOWED, "Cannot delete customer because they have existing measurement records");
        }

        customerRepository.delete(customer);
    }
}
