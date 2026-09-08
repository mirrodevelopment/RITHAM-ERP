package com.ritham.erp.module.customer.repository;

import com.ritham.erp.module.customer.entity.CustomerMeasurement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerMeasurementRepository extends JpaRepository<CustomerMeasurement, Long> {

    List<CustomerMeasurement> findByCustomerMobileOrderByUpdatedAtDesc(String customerMobile);

    boolean existsByCustomerMobile(String customerMobile);

    boolean existsByCustomerMobileAndGarmentType(String customerMobile, String garmentType);

    Optional<CustomerMeasurement> findByCustomerMobileAndGarmentType(String customerMobile, String garmentType);

    Optional<CustomerMeasurement> findFirstByCustomerMobileOrderByUpdatedAtDesc(String customerMobile);

    @Query("""
        SELECT cm FROM CustomerMeasurement cm
        WHERE LOWER(cm.customerName) LIKE LOWER(CONCAT('%', :query, '%'))
           OR cm.customerMobile LIKE CONCAT('%', :query, '%')
           OR LOWER(cm.garmentType) LIKE LOWER(CONCAT('%', :query, '%'))
        ORDER BY cm.updatedAt DESC
    """)
    Page<CustomerMeasurement> search(@Param("query") String query, Pageable pageable);
}
