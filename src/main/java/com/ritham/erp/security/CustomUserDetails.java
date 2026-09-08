package com.ritham.erp.security;

import com.ritham.erp.common.constants.RoleConstants;
import com.ritham.erp.module.employee.entity.Employee;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security {@link UserDetails} adapter wrapping an {@link Employee}.
 */
@Getter
public class CustomUserDetails implements UserDetails {

    private final Employee employee;

    public CustomUserDetails(Employee employee) {
        this.employee = employee;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(employee.getRole().getName()));
    }

    @Override
    public String getPassword() {
        return employee.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return employee.getUsername() != null ? employee.getUsername() : employee.getMobileNumber();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return Boolean.TRUE.equals(employee.getIsActive());
    }

    // ── Convenience accessors used in controllers ─────────────────────────────

    public Long getEmployeeId() {
        return employee.getId();
    }

    public String getFullName() {
        return employee.getFullName();
    }

    public String getRoleName() {
        return employee.getRole().getName();
    }

    public Long getBranchId() {
        return employee.getBranch() != null ? employee.getBranch().getId() : null;
    }

    public String getBranchName() {
        return employee.getBranch() != null ? employee.getBranch().getName() : null;
    }

    public String getBranchCode() {
        return employee.getBranch() != null ? employee.getBranch().getBranchCode() : null;
    }

    public boolean isAdmin() {
        return RoleConstants.ADMIN.equals(getRoleName()) || "ADMIN".equals(getRoleName());
    }

    public boolean isGlobalAdmin() {
        return isAdmin();
    }
}
