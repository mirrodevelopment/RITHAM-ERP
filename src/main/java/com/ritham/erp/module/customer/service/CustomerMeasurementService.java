package com.ritham.erp.module.customer.service;

import com.ritham.erp.common.exception.AppException;
import com.ritham.erp.common.exception.ErrorCode;
import com.ritham.erp.common.response.PageResponse;
import tools.jackson.databind.ObjectMapper;
import com.ritham.erp.module.customer.dto.CustomerMeasurementResponse;
import com.ritham.erp.module.customer.dto.SaveCustomerMeasurementRequest;
import com.ritham.erp.module.customer.entity.Customer;
import com.ritham.erp.module.customer.entity.CustomerMeasurement;
import com.ritham.erp.module.customer.repository.CustomerMeasurementRepository;
import com.ritham.erp.module.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerMeasurementService {

    private final CustomerMeasurementRepository measurementRepository;
    private final CustomerRepository customerRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public CustomerMeasurementResponse saveOrUpdate(SaveCustomerMeasurementRequest req) {
        String cleanMobile = req.getCustomerMobile().trim();
        String cleanName = req.getCustomerName().trim();
        String garmentType = req.getGarmentType().trim().toUpperCase();

        // Auto-register customer in directory if not present
        Customer customer = customerRepository.findByCustomerMobile(cleanMobile).orElseGet(() -> {
            log.info("Auto-registering customer {} ({}) during measurement save", cleanName, cleanMobile);
            return customerRepository.save(Customer.builder()
                    .customerMobile(cleanMobile)
                    .customerName(cleanName)
                    .build());
        });

        // Update customer name if provided
        if (!cleanName.equalsIgnoreCase(customer.getCustomerName())) {
            customer.setCustomerName(cleanName);
            customerRepository.save(customer);
        }

        Map<String, String> measurementsMap = req.getMeasurements() != null ? req.getMeasurements() : Collections.emptyMap();
        String measurementsJson = serializeMeasurements(measurementsMap);

        String cleanNotes = req.getNotes();
        if (cleanNotes != null && (cleanNotes.contains("Order #") || cleanNotes.contains("Auto-synced"))) {
            cleanNotes = null;
        }

        CustomerMeasurement entity = measurementRepository
                .findByCustomerMobileAndGarmentType(cleanMobile, garmentType)
                .orElse(null);

        if (entity != null) {
            entity.setCustomerName(cleanName);
            entity.setLining(null);
            entity.setMeasurementsJson(measurementsJson);
            entity.setNotes(cleanNotes != null ? cleanNotes.trim() : null);
        } else {
            entity = CustomerMeasurement.builder()
                    .customerMobile(cleanMobile)
                    .customerName(cleanName)
                    .garmentType(garmentType)
                    .lining(null)
                    .measurementsJson(measurementsJson)
                    .notes(cleanNotes != null ? cleanNotes.trim() : null)
                    .build();
        }

        CustomerMeasurement saved = measurementRepository.save(entity);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<CustomerMeasurementResponse> getByCustomerMobile(String mobile) {
        String cleanMobile = mobile.trim();
        return measurementRepository.findByCustomerMobileOrderByUpdatedAtDesc(cleanMobile)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<CustomerMeasurementResponse> getLatestByCustomerMobile(String mobile) {
        String cleanMobile = mobile.trim();
        return measurementRepository.findFirstByCustomerMobileOrderByUpdatedAtDesc(cleanMobile)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Optional<CustomerMeasurementResponse> getByCustomerMobileAndGarment(String mobile, String garmentType) {
        return measurementRepository.findByCustomerMobileAndGarmentType(mobile.trim(), garmentType.trim().toUpperCase())
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerMeasurementResponse> getAllMeasurements(String search, Pageable pageable) {
        Page<CustomerMeasurement> page;
        if (search != null && !search.trim().isEmpty()) {
            page = measurementRepository.search(search.trim(), pageable);
        } else {
            page = measurementRepository.findAll(pageable);
        }
        return PageResponse.of(page.map(this::toResponse));
    }

    @Transactional
    public void deleteMeasurement(Long id) {
        if (!measurementRepository.existsById(id)) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        measurementRepository.deleteById(id);
    }

    public CustomerMeasurementResponse toResponse(CustomerMeasurement entity) {
        String notes = entity.getNotes();
        if (notes != null && (notes.contains("Order #") || notes.contains("Auto-synced"))) {
            notes = null;
        }
        return CustomerMeasurementResponse.builder()
                .id(entity.getId())
                .customerMobile(entity.getCustomerMobile())
                .customerName(entity.getCustomerName())
                .garmentType(entity.getGarmentType())
                .lining(null)
                .measurements(parseMeasurementsJson(entity.getMeasurementsJson()))
                .notes(notes)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private String serializeMeasurements(Map<String, String> measurements) {
        if (measurements == null || measurements.isEmpty()) return "{}";
        try {
            return objectMapper.writeValueAsString(measurements);
        } catch (Exception e) {
            log.warn("Failed to serialize measurements map: {}", e.getMessage());
            return "{}";
        }
    }

    private Map<String, String> parseMeasurementsJson(String json) {
        if (json == null || json.isBlank() || json.equals("{}")) return Collections.emptyMap();
        try {
            Map<?, ?> raw = objectMapper.readValue(json, Map.class);
            Map<String, String> map = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    map.put(entry.getKey().toString(), entry.getValue().toString());
                }
            }
            return map;
        } catch (Exception e) {
            log.warn("Failed to parse measurements JSON: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}
