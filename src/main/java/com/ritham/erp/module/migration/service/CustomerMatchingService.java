package com.ritham.erp.module.migration.service;

import com.ritham.erp.module.customer.entity.Customer;
import com.ritham.erp.module.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Matches extracted customer data against existing ERP customers.
 *
 * <p>Matching priority:
 * <ol>
 *   <li>Exact 10-digit mobile match — highest confidence, auto-candidate</li>
 *   <li>No match — reviewer must choose Create New or enter a different mobile</li>
 * </ol>
 *
 * <p>This service NEVER creates customers automatically; it only suggests matches.
 * The final decision is always made by the human reviewer via the Review Desk.
 */
import com.ritham.erp.module.customer.entity.CustomerMeasurement;
import com.ritham.erp.module.customer.repository.CustomerMeasurementRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerMatchingService {

    private final CustomerRepository customerRepository;
    private final CustomerMeasurementRepository measurementRepository;

    /** Result returned to the Review Desk for the reviewer to choose from. */
    public record MatchResult(
            MatchType matchType,
            List<CustomerCandidate> candidates,
            boolean measurementExists,
            String existingGarmentType,
            String existingUpdatedAt
    ) {
        public MatchResult(MatchType matchType, List<CustomerCandidate> candidates) {
            this(matchType, candidates, false, null, null);
        }
    }

    public record CustomerCandidate(
            String mobile,
            String name,
            int totalOrders,
            MatchConfidence confidence,
            boolean measurementExists
    ) {
        public CustomerCandidate(String mobile, String name, int totalOrders, MatchConfidence confidence) {
            this(mobile, name, totalOrders, confidence, false);
        }
    }

    public enum MatchType {
        /** Exact mobile match — very high confidence. */
        EXACT_MOBILE,
        /** No match found — reviewer must choose to create new customer. */
        NO_MATCH
    }

    public enum MatchConfidence { HIGH, MEDIUM, LOW }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Find matching ERP customers for the given extracted mobile number.
     *
     * @param extractedMobile mobile number from OCR (may be null or invalid)
     * @param extractedName   customer name from OCR (may be null)
     * @return MatchResult with candidates; never null
     */
    public MatchResult findMatches(String extractedMobile, String extractedName) {
        return findMatches(extractedMobile, extractedName, null);
    }

    /**
     * Find matching ERP customers and verify if measurements exist for the garment type.
     */
    public MatchResult findMatches(String extractedMobile, String extractedName, String garmentType) {
        List<CustomerCandidate> candidates = new ArrayList<>();

        // Priority 1: exact mobile match
        if (extractedMobile != null && extractedMobile.matches("[6-9]\\d{9}")) {
            Optional<Customer> exact = customerRepository.findByCustomerMobile(extractedMobile);
            if (exact.isPresent()) {
                Customer c = exact.get();

                boolean measExists = false;
                String matchedGarment = null;
                String updatedAtStr = null;

                if (garmentType != null && !garmentType.isBlank() && !"OTHER".equals(garmentType)) {
                    Optional<CustomerMeasurement> cm = measurementRepository.findByCustomerMobileAndGarmentType(extractedMobile, garmentType);
                    if (cm.isPresent()) {
                        measExists = true;
                        matchedGarment = cm.get().getGarmentType();
                        updatedAtStr = cm.get().getUpdatedAt() != null ? cm.get().getUpdatedAt().toString() : null;
                    }
                }
                if (!measExists) {
                    Optional<CustomerMeasurement> anyCm = measurementRepository.findFirstByCustomerMobileOrderByUpdatedAtDesc(extractedMobile);
                    if (anyCm.isPresent()) {
                        measExists = true;
                        matchedGarment = anyCm.get().getGarmentType();
                        updatedAtStr = anyCm.get().getUpdatedAt() != null ? anyCm.get().getUpdatedAt().toString() : null;
                    }
                }

                candidates.add(new CustomerCandidate(
                        c.getCustomerMobile(),
                        c.getCustomerName(),
                        c.getTotalOrders() != null ? c.getTotalOrders() : 0,
                        MatchConfidence.HIGH,
                        measExists
                ));
                log.debug("Exact mobile match found: {} -> {}, measurementExists={}", extractedMobile, c.getCustomerName(), measExists);
                return new MatchResult(MatchType.EXACT_MOBILE, candidates, measExists, matchedGarment, updatedAtStr);
            }
        }

        // No match
        log.debug("No ERP customer match for mobile={}, name={}", extractedMobile, extractedName);
        return new MatchResult(MatchType.NO_MATCH, candidates, false, null, null);
    }

    /**
     * Returns true if the given mobile already exists in the ERP customer table.
     */
    public boolean customerExists(String mobile) {
        if (mobile == null) return false;
        return customerRepository.findByCustomerMobile(mobile).isPresent();
    }
}
