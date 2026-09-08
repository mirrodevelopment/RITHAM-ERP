package com.ritham.erp.module.order.repository;

import com.ritham.erp.module.order.entity.OrderMeasurement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderMeasurementRepository extends JpaRepository<OrderMeasurement, Long> {

    Optional<OrderMeasurement> findByOrderId(Long orderId);

    void deleteByOrderId(Long orderId);
}
