package com.ritham.erp.security;

import com.ritham.erp.common.exception.AppException;
import com.ritham.erp.common.exception.ErrorCode;
import com.ritham.erp.module.employee.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads an {@link com.ritham.erp.module.employee.entity.Employee} from the
 * database
 * and wraps it as {@link CustomUserDetails} for Spring Security.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final EmployeeRepository employeeRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        if (identifier == null || identifier.isBlank()) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS, "Username or mobile number is required");
        }
        String clean = identifier.trim();

        return employeeRepository.findByUsername(clean)
                .or(() -> employeeRepository.findByMobileNumber(clean))
                .or(() -> employeeRepository.findByEmployeeCode(clean.toUpperCase()))
                .map(CustomUserDetails::new)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_CREDENTIALS,
                        "Invalid username or password"));
    }
}
