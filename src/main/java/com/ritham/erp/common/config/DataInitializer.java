package com.ritham.erp.common.config;

import com.ritham.erp.module.customer.repository.CustomerRepository;
import com.ritham.erp.module.employee.repository.DepartmentRepository;
import com.ritham.erp.module.employee.repository.EmployeeRepository;
import com.ritham.erp.module.employee.repository.RoleRepository;
import com.ritham.erp.module.order.repository.CustomerOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs on application startup after Flyway database migrations.
 *
 * Responsibilities:
 * <ul>
 *   <li>Logs startup summary of native database records.</li>
 *   <li>All system user passwords and credentials are managed natively in database Flyway migrations.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final EmployeeRepository      employeeRepository;
    private final RoleRepository          roleRepository;
    private final DepartmentRepository    departmentRepository;
    private final CustomerRepository      customerRepository;
    private final CustomerOrderRepository customerOrderRepository;

    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Override
    @Transactional
    public void run(String... args) {
        initSystemPasswords();
        logStartupSummary();
    }

    private void initSystemPasswords() {
        encodeIfNeeded("admin",          "Admin@123");
        encodeIfNeeded("reception",      "Reception@123");
        encodeIfNeeded("production",     "Production@123");
        encodeIfNeeded("manager",        "Manager@123");

        // Branch accounts: only encode if stored value is not already a valid BCrypt hash.
        // Do NOT force-reset to default — preserves passwords changed by an admin.
        encodeIfNeeded("reception_cbe",  "Reception@123");
        encodeIfNeeded("production_cbe", "Production@123");
        encodeIfNeeded("manager_cbe",    "Manager@123");

        employeeRepository.flush();
        log.info("System user passwords verified on startup.");
    }

    /**
     * Only re-encodes the password if the stored hash is not already a valid BCrypt hash.
     * Prevents unnecessary BCrypt work (cost=12, ~300ms each) on every server restart.
     */
    private void encodeIfNeeded(String username, String defaultPassword) {
        employeeRepository.findByUsername(username).ifPresent(e -> {
            String hash = e.getPasswordHash();
            if (hash == null || (!hash.startsWith("$2a$") && !hash.startsWith("$2b$"))) {
                e.setPasswordHash(passwordEncoder.encode(defaultPassword));
                employeeRepository.saveAndFlush(e);
                log.info("Password re-encoded for system user: {}", username);
            }
        });
    }

    private void logStartupSummary() {
        long roleCount       = roleRepository.count();
        long departmentCount = departmentRepository.count();
        long employeeCount   = employeeRepository.count();
        long customerCount   = customerRepository.count();
        long orderCount      = customerOrderRepository.count();

        log.info("📊 Native Database Summary — Roles: {} | Departments: {} | Employees: {} | Customers: {} | Orders: {}",
                roleCount, departmentCount, employeeCount, customerCount, orderCount);
        log.info("🚀 Ritham ERP started successfully. API base: /api");
    }
}
