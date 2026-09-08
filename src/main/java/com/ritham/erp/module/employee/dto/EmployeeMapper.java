package com.ritham.erp.module.employee.dto;

import com.ritham.erp.common.constants.RoleConstants;
import com.ritham.erp.module.employee.entity.Department;
import com.ritham.erp.module.employee.entity.Employee;
import com.ritham.erp.module.employee.entity.Role;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class EmployeeMapper {

    private static final Map<String, String> ROLE_LABELS = Map.of(
            RoleConstants.ADMIN, "Administrator",
            RoleConstants.OPERATIONS_MANAGER, "Operations Manager",
            RoleConstants.PRODUCTION_EMPLOYEE, "Production Floor",
            RoleConstants.RECEPTION, "Reception"
    );

    public EmployeeResponse toResponse(Employee employee) {
        if (employee == null) {
            return null;
        }

        Role role = employee.getRole();
        Department dept = employee.getDepartment();
        com.ritham.erp.module.branch.entity.Branch branch = employee.getBranch();

        return EmployeeResponse.builder()
                .id(employee.getId())
                .employeeCode(employee.getEmployeeCode())
                .fullName(employee.getFullName())
                .mobileNumber(employee.getMobileNumber())
                .email(employee.getEmail())
                .username(employee.getUsername())
                .roleId(role != null ? role.getId() : null)
                .role(role != null ? role.getName() : null)
                .roleLabel(role != null ? ROLE_LABELS.getOrDefault(role.getName(), role.getName()) : null)
                .departmentId(dept != null ? dept.getId() : null)
                .department(dept != null ? dept.getName() : null)
                .branchId(branch != null ? branch.getId() : null)
                .branchName(branch != null ? branch.getName() : null)
                .branchCode(branch != null ? branch.getBranchCode() : null)
                .stage(employee.getStage())
                .advance(employee.getAdvance() != null ? employee.getAdvance() : 0)
                .joiningDate(employee.getJoiningDate())
                .isActive(employee.getIsActive())
                .createdAt(employee.getCreatedAt())
                .updatedAt(employee.getUpdatedAt())
                .build();
    }

    public RoleResponse toRoleResponse(Role role) {
        if (role == null) return null;
        return RoleResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .build();
    }

    public DepartmentResponse toDepartmentResponse(Department dept) {
        if (dept == null) return null;
        return DepartmentResponse.builder()
                .id(dept.getId())
                .name(dept.getName())
                .build();
    }
}
