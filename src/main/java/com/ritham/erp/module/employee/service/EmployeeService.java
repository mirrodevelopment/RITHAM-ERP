package com.ritham.erp.module.employee.service;

import com.ritham.erp.common.constants.AppConstants;
import com.ritham.erp.common.constants.RoleConstants;
import com.ritham.erp.common.exception.AppException;
import com.ritham.erp.common.exception.ErrorCode;
import com.ritham.erp.common.response.PageResponse;
import com.ritham.erp.module.employee.dto.*;
import com.ritham.erp.module.employee.entity.Department;
import com.ritham.erp.module.employee.entity.Employee;
import com.ritham.erp.module.employee.entity.Role;
import com.ritham.erp.module.employee.repository.DepartmentRepository;
import com.ritham.erp.module.employee.repository.EmployeeRepository;
import com.ritham.erp.module.employee.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;
    private final com.ritham.erp.module.branch.repository.BranchRepository branchRepository;
    private final EmployeeMapper employeeMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public PageResponse<EmployeeResponse> getEmployees(String search, Pageable pageable) {
        Long branchId = com.ritham.erp.security.BranchContext.getBranchId();
        String q = search != null ? search.trim() : "";
        Page<Employee> page = employeeRepository.searchAllScoped(branchId, q, pageable);

        Page<EmployeeResponse> dtoPage = page.map(employeeMapper::toResponse);
        return PageResponse.of(dtoPage);
    }

    @Transactional(readOnly = true)
    public EmployeeResponse getEmployeeById(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));

        verifyEmployeeBranchAccess(employee);

        return employeeMapper.toResponse(employee);
    }

    @Transactional
    public EmployeeResponse createEmployee(CreateEmployeeRequest request) {
        if (employeeRepository.existsByMobileNumber(request.getMobileNumber().trim())) {
            throw new AppException(ErrorCode.EMPLOYEE_MOBILE_TAKEN);
        }

        Role role = null;
        if (request.getRoleId() != null) {
            role = roleRepository.findById(request.getRoleId()).orElse(null);
        }
        if (role == null) {
            role = roleRepository.findByName(RoleConstants.PRODUCTION_EMPLOYEE)
                    .or(() -> roleRepository.findByName("PRODUCTION_EMPLOYEE"))
                    .or(() -> roleRepository.findById(3L))
                    .orElseGet(() -> roleRepository.findAll().stream()
                            .filter(r -> !r.getName().contains("ADMIN"))
                            .findFirst()
                            .orElse(null));
        }

        Department dept = null;
        if (request.getDepartmentId() != null) {
            dept = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Department not found"));
        }

        // Branch assignment
        Long branchId = request.getBranchId() != null ? request.getBranchId() : com.ritham.erp.security.BranchContext.getBranchId();
        com.ritham.erp.module.branch.entity.Branch branch = null;
        if (branchId != null) {
            branch = branchRepository.findById(branchId).orElse(null);
        } else {
            // Default to Branch 1 if not specified
            branch = branchRepository.findById(1L).orElse(null);
        }

        String employeeCode = generateEmployeeCode();

        Employee employee = Employee.builder()
                .employeeCode(employeeCode)
                .fullName(request.getFullName().trim())
                .mobileNumber(request.getMobileNumber().trim())
                .email(request.getEmail() != null ? request.getEmail().trim() : null)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .department(dept)
                .branch(branch)
                .stage(request.getStage() != null ? request.getStage().trim() : null)
                .advance(request.getAdvance() != null ? request.getAdvance() : 0)
                .joiningDate(request.getJoiningDate() != null ? request.getJoiningDate() : java.time.LocalDate.now())
                .isActive(true)
                .build();

        Employee saved = employeeRepository.save(employee);
        return employeeMapper.toResponse(saved);
    }

    @Transactional
    public EmployeeResponse updateEmployee(Long id, UpdateEmployeeRequest request) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));

        verifyEmployeeBranchAccess(employee);

        // Mobile uniqueness check if changed
        if (!employee.getMobileNumber().equalsIgnoreCase(request.getMobileNumber().trim())) {
            if (employeeRepository.existsByMobileNumber(request.getMobileNumber().trim())) {
                throw new AppException(ErrorCode.EMPLOYEE_MOBILE_TAKEN);
            }
            employee.setMobileNumber(request.getMobileNumber().trim());
        }

        if (request.getRoleId() != null) {
            Role role = roleRepository.findById(request.getRoleId())
                    .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Role not found"));
            employee.setRole(role);
        }

        Department dept = null;
        if (request.getDepartmentId() != null) {
            dept = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Department not found"));
            employee.setDepartment(dept);
        }

        employee.setFullName(request.getFullName().trim());
        employee.setEmail(request.getEmail() != null ? request.getEmail().trim() : null);
        if (request.getStage() != null) {
            employee.setStage(request.getStage().trim());
        }
        if (request.getAdvance() != null) {
            employee.setAdvance(request.getAdvance());
        }
        if (request.getJoiningDate() != null) {
            employee.setJoiningDate(request.getJoiningDate());
        }
        if (request.getBranchId() != null) {
            branchRepository.findById(request.getBranchId()).ifPresent(employee::setBranch);
        }

        Employee saved = employeeRepository.save(employee);
        return employeeMapper.toResponse(saved);
    }

    @Transactional
    public void updatePassword(Long id, UpdatePasswordRequest request) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));

        verifyEmployeeBranchAccess(employee);

        employee.setPasswordHash(passwordEncoder.encode(request.getPassword().trim()));
        employeeRepository.save(employee);
    }

    @Transactional
    public EmployeeResponse toggleStatus(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));

        verifyEmployeeBranchAccess(employee);

        if ("admin".equalsIgnoreCase(employee.getUsername()) || "EMP-001".equalsIgnoreCase(employee.getEmployeeCode())) {
            throw new AppException(ErrorCode.OPERATION_NOT_ALLOWED, "Primary system administrator account cannot be deactivated");
        }

        employee.setIsActive(!employee.getIsActive());
        Employee saved = employeeRepository.save(employee);
        return employeeMapper.toResponse(saved);
    }

    @Transactional
    public void deleteEmployee(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));

        verifyEmployeeBranchAccess(employee);

        if ("admin".equalsIgnoreCase(employee.getUsername()) || "EMP-001".equalsIgnoreCase(employee.getEmployeeCode())) {
            throw new AppException(ErrorCode.OPERATION_NOT_ALLOWED, "Primary system administrator account cannot be deactivated or deleted");
        }

        employee.setIsActive(false);
        employeeRepository.save(employee);
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> getAllRoles() {
        return roleRepository.findAll().stream()
                .map(employeeMapper::toRoleResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DepartmentResponse> getAllDepartments() {
        return departmentRepository.findAll().stream()
                .map(employeeMapper::toDepartmentResponse)
                .toList();
    }

    // ── Branch isolation ───────────────────────────────────────────────────

    /**
     * Verifies the caller's branch matches the employee's branch.
     * System Administrators (ROLE_ADMIN) or users with null branchId in BranchContext may access any employee record.
     * Branch staff may only access employees assigned to their own branch.
     * Employees with no branch assigned (e.g. the global admin account) are
     * only accessible by the global admin.
     *
     * @throws AppException ACCESS_DENIED (403) if branches do not match.
     */
    private void verifyEmployeeBranchAccess(Employee employee) {
        if (isCurrentCallerAdmin()) {
            // System Administrator can access all employee records across all branches
            return;
        }
        Long callerBranchId = com.ritham.erp.security.BranchContext.getBranchId();
        if (callerBranchId == null) {
            // Global admin — unrestricted.
            return;
        }
        Long empBranchId = (employee.getBranch() != null) ? employee.getBranch().getId() : null;
        if (!callerBranchId.equals(empBranchId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }

    private boolean isCurrentCallerAdmin() {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> RoleConstants.ADMIN.equals(a.getAuthority()) || "ADMIN".equals(a.getAuthority()));
    }

    private String generateEmployeeCode() {
        // Use max existing sequence (not count) to avoid collisions after employee deletions
        long nextSeq = employeeRepository.findMaxEmployeeCodeSequence() + 1;
        return String.format(AppConstants.EMPLOYEE_CODE_PREFIX + "%03d", nextSeq);
    }
}
