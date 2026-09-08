package com.ritham.erp.module.employee;

import com.ritham.erp.common.config.SecurityConfig;
import com.ritham.erp.module.employee.controller.EmployeeController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class EmployeeRbacSecurityTest {

    @Test
    @DisplayName("SecurityConfig must enable method security (@EnableMethodSecurity)")
    void securityConfigEnablesMethodSecurity() {
        assertTrue(SecurityConfig.class.isAnnotationPresent(EnableMethodSecurity.class),
                "SecurityConfig must be annotated with @EnableMethodSecurity for @PreAuthorize to take effect");
    }

    @Test
    @DisplayName("EmployeeController.createEmployee must be protected with @PreAuthorize(\"hasRole('ADMIN')\")")
    void createEmployeeProtectedByAdminRole() throws NoSuchMethodException {
        Method method = EmployeeController.class.getMethod("createEmployee",
                com.ritham.erp.module.employee.dto.CreateEmployeeRequest.class);
        PreAuthorize preAuth = method.getAnnotation(PreAuthorize.class);

        assertNotNull(preAuth, "createEmployee must have @PreAuthorize annotation");
        assertEquals("hasRole('ADMIN')", preAuth.value(), "createEmployee must be restricted to ADMIN role only");
    }

    @Test
    @DisplayName("EmployeeController.updateEmployee must be protected with @PreAuthorize(\"hasRole('ADMIN')\")")
    void updateEmployeeProtectedByAdminRole() throws NoSuchMethodException {
        Method method = EmployeeController.class.getMethod("updateEmployee",
                Long.class, com.ritham.erp.module.employee.dto.UpdateEmployeeRequest.class);
        PreAuthorize preAuth = method.getAnnotation(PreAuthorize.class);

        assertNotNull(preAuth, "updateEmployee must have @PreAuthorize annotation");
        assertEquals("hasRole('ADMIN')", preAuth.value(), "updateEmployee must be restricted to ADMIN role only");
    }

    @Test
    @DisplayName("EmployeeController.toggleStatus must be protected with @PreAuthorize(\"hasRole('ADMIN')\")")
    void toggleStatusProtectedByAdminRole() throws NoSuchMethodException {
        Method method = EmployeeController.class.getMethod("toggleStatus", Long.class);
        PreAuthorize preAuth = method.getAnnotation(PreAuthorize.class);

        assertNotNull(preAuth, "toggleStatus must have @PreAuthorize annotation");
        assertEquals("hasRole('ADMIN')", preAuth.value(), "toggleStatus must be restricted to ADMIN role only");
    }

    @Test
    @DisplayName("EmployeeController.deleteEmployee must be protected with @PreAuthorize(\"hasRole('ADMIN')\")")
    void deleteEmployeeProtectedByAdminRole() throws NoSuchMethodException {
        Method method = EmployeeController.class.getMethod("deleteEmployee", Long.class);
        PreAuthorize preAuth = method.getAnnotation(PreAuthorize.class);

        assertNotNull(preAuth, "deleteEmployee must have @PreAuthorize annotation");
        assertEquals("hasRole('ADMIN')", preAuth.value(), "deleteEmployee must be restricted to ADMIN role only");
    }

    @Test
    @DisplayName("Employee read-only endpoints (getEmployees, getEmployeeById, getRoles, getDepartments) must not be blocked to admin-only")
    void readOnlyEndpointsNotBlockedByAdminOnly() {
        Arrays.stream(EmployeeController.class.getMethods())
                .filter(m -> m.getName().startsWith("get"))
                .forEach(m -> {
                    PreAuthorize preAuth = m.getAnnotation(PreAuthorize.class);
                    if (preAuth != null) {
                        assertFalse(preAuth.value().contains("hasRole('ADMIN')"),
                                "Read-only method " + m.getName() + " should not be restricted exclusively to ADMIN");
                    }
                });
    }
}
