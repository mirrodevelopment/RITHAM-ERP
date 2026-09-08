package com.ritham.erp.module.order;

import com.ritham.erp.module.customer.entity.Customer;
import com.ritham.erp.module.customer.repository.CustomerRepository;
import com.ritham.erp.module.order.entity.CustomerOrder;
import com.ritham.erp.module.order.repository.CustomerOrderRepository;
import jakarta.persistence.Column;
import jakarta.persistence.Version;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verification and regression tests for TASK-031:
 * Adding optimistic locking (@Version) to CustomerOrder to prevent lost updates
 * during concurrent order modifications.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class CustomerOrderOptimisticLockingTest {

    private static final String TEST_MOBILE_CONCURRENT = "9123456780";
    private static final String TEST_MOBILE_SEQUENTIAL = "9988776655";

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CustomerOrderRepository orderRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUpCustomers() {
        if (!customerRepository.existsById(TEST_MOBILE_CONCURRENT)) {
            customerRepository.save(Customer.builder()
                    .customerMobile(TEST_MOBILE_CONCURRENT)
                    .customerName("Concurrent Test Customer")
                    .build());
        }
        if (!customerRepository.existsById(TEST_MOBILE_SEQUENTIAL)) {
            customerRepository.save(Customer.builder()
                    .customerMobile(TEST_MOBILE_SEQUENTIAL)
                    .customerName("Sequential Test Customer")
                    .build());
        }
    }

    @Test
    @DisplayName("TASK-031: CustomerOrder has @Version annotation on version field")
    void customerOrderHasVersionAnnotation() throws NoSuchFieldException {
        Field versionField = CustomerOrder.class.getDeclaredField("version");
        assertNotNull(versionField, "version field must exist on CustomerOrder");

        Version versionAnnotation = versionField.getAnnotation(Version.class);
        assertNotNull(versionAnnotation, "CustomerOrder.version must be annotated with @Version");

        Column columnAnnotation = versionField.getAnnotation(Column.class);
        assertNotNull(columnAnnotation, "CustomerOrder.version must have @Column annotation");
        assertEquals("version", columnAnnotation.name(), "Column name must be 'version'");
    }

    @Test
    @DisplayName("TASK-031: Default version is 0L on new instance and builder")
    void defaultVersionIsZero() {
        CustomerOrder orderFromNoArg = new CustomerOrder();
        assertEquals(0L, orderFromNoArg.getVersion());

        CustomerOrder orderFromBuilder = CustomerOrder.builder()
                .orderNumber("ORD-TEST-VER-0")
                .customerMobile(TEST_MOBILE_CONCURRENT)
                .build();
        assertEquals(0L, orderFromBuilder.getVersion());
    }

    @Test
    @DisplayName("TASK-031: Concurrent update with stale version triggers ObjectOptimisticLockingFailureException")
    void concurrentUpdateWithStaleVersionThrowsOptimisticLockException() {
        TransactionTemplate tt = new TransactionTemplate(transactionManager);
        tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // 1. Create and persist an order
        String orderNumber = "ORD-OPT-" + System.currentTimeMillis();
        Long orderId = tt.execute(status -> {
            CustomerOrder newOrder = CustomerOrder.builder()
                    .orderNumber(orderNumber)
                    .customerMobile(TEST_MOBILE_CONCURRENT)
                    .orderDate(LocalDateTime.now())
                    .deliveryDate(LocalDateTime.now().plusDays(3))
                    .status("PENDING")
                    .paymentStatus("PENDING")
                    .totalAmount(new BigDecimal("1500.00"))
                    .advanceAmount(BigDecimal.ZERO)
                    .paidAmount(BigDecimal.ZERO)
                    .discountAmount(BigDecimal.ZERO)
                    .build();
            CustomerOrder saved = orderRepository.saveAndFlush(newOrder);
            return saved.getId();
        });

        assertNotNull(orderId);

        // 2. Client A reads the order at version 0
        CustomerOrder clientAOrder = tt.execute(status ->
                orderRepository.findById(orderId).orElseThrow()
        );
        assertNotNull(clientAOrder);
        assertEquals(0L, clientAOrder.getVersion());

        // 3. Client B reads the same order at version 0, updates it to "IN_PRODUCTION", and commits
        CustomerOrder updatedB = tt.execute(status -> {
            CustomerOrder clientBOrder = orderRepository.findById(orderId).orElseThrow();
            assertEquals(0L, clientBOrder.getVersion());
            clientBOrder.setStatus("IN_PRODUCTION");
            return orderRepository.saveAndFlush(clientBOrder);
        });

        // Client B's update incremented version to 1
        assertNotNull(updatedB);
        assertEquals(1L, updatedB.getVersion());
        assertEquals("IN_PRODUCTION", updatedB.getStatus());

        // 4. Client A attempts to update the order using stale copy (version 0).
        // This must be detected and rejected with ObjectOptimisticLockingFailureException.
        clientAOrder.setStatus("CANCELLED");

        assertThrows(ObjectOptimisticLockingFailureException.class, () -> {
            tt.execute(status -> orderRepository.saveAndFlush(clientAOrder));
        }, "Saving detached entity with stale version must throw ObjectOptimisticLockingFailureException");

        // 5. Verify database state was protected: status is still Client B's "IN_PRODUCTION", not "CANCELLED"
        CustomerOrder finalOrderState = tt.execute(status ->
                orderRepository.findById(orderId).orElseThrow()
        );
        assertNotNull(finalOrderState);
        assertEquals("IN_PRODUCTION", finalOrderState.getStatus(), "Database state should retain Client B's update");
        assertEquals(1L, finalOrderState.getVersion(), "Database version should remain 1");
    }

    @Test
    @DisplayName("TASK-031: Sequential updates successfully increment version number")
    void sequentialUpdatesIncrementVersion() {
        TransactionTemplate tt = new TransactionTemplate(transactionManager);
        tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        String orderNumber = "ORD-SEQ-" + System.currentTimeMillis();
        Long orderId = tt.execute(status -> {
            CustomerOrder newOrder = CustomerOrder.builder()
                    .orderNumber(orderNumber)
                    .customerMobile(TEST_MOBILE_SEQUENTIAL)
                    .orderDate(LocalDateTime.now())
                    .deliveryDate(LocalDateTime.now().plusDays(2))
                    .status("PENDING")
                    .paymentStatus("PENDING")
                    .totalAmount(new BigDecimal("2000.00"))
                    .advanceAmount(BigDecimal.ZERO)
                    .paidAmount(BigDecimal.ZERO)
                    .discountAmount(BigDecimal.ZERO)
                    .build();
            return orderRepository.saveAndFlush(newOrder).getId();
        });

        // Step 1: Update to IN_PRODUCTION (0 -> 1)
        CustomerOrder orderStep1 = tt.execute(status -> {
            CustomerOrder order = orderRepository.findById(orderId).orElseThrow();
            assertEquals(0L, order.getVersion());
            order.setStatus("IN_PRODUCTION");
            return orderRepository.saveAndFlush(order);
        });
        assertEquals(1L, orderStep1.getVersion());

        // Step 2: Update to READY_TO_DELIVERY (1 -> 2)
        CustomerOrder orderStep2 = tt.execute(status -> {
            CustomerOrder order = orderRepository.findById(orderId).orElseThrow();
            assertEquals(1L, order.getVersion());
            order.setStatus("READY_TO_DELIVERY");
            return orderRepository.saveAndFlush(order);
        });
        assertEquals(2L, orderStep2.getVersion());

        // Step 3: Record payment update (2 -> 3)
        CustomerOrder orderStep3 = tt.execute(status -> {
            CustomerOrder order = orderRepository.findById(orderId).orElseThrow();
            assertEquals(2L, order.getVersion());
            order.setPaidAmount(new BigDecimal("2000.00"));
            order.setPaymentStatus("PAID");
            return orderRepository.saveAndFlush(order);
        });
        assertEquals(3L, orderStep3.getVersion());
        assertEquals("PAID", orderStep3.getPaymentStatus());
    }
}
