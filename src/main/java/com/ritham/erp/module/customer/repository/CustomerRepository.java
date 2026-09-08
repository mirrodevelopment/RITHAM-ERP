package com.ritham.erp.module.customer.repository;

import com.ritham.erp.module.customer.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, String> {

    Optional<Customer> findByCustomerMobile(String customerMobile);

    boolean existsByCustomerMobile(String customerMobile);

    @Query("""
        SELECT c FROM Customer c
        WHERE LOWER(c.customerName) LIKE LOWER(CONCAT('%', :query, '%'))
           OR c.customerMobile LIKE CONCAT('%', :query, '%')
    """)
    Page<Customer> search(@Param("query") String query, Pageable pageable);

    /**
     * Batch lookup: resolve multiple mobile numbers to Customer entities in ONE query.
     * Used by OrderService to eliminate N+1 per-order customer name lookups.
     */
    @Query("SELECT c FROM Customer c WHERE c.customerMobile IN :mobiles")
    java.util.List<Customer> findAllByCustomerMobileIn(@Param("mobiles") java.util.Collection<String> mobiles);
}
