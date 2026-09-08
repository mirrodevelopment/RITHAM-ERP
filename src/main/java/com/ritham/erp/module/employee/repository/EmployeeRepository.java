package com.ritham.erp.module.employee.repository;

import com.ritham.erp.module.employee.entity.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByUsername(String username);

    Optional<Employee> findByMobileNumber(String mobileNumber);

    Optional<Employee> findByEmployeeCode(String employeeCode);

    boolean existsByUsername(String username);

    boolean existsByMobileNumber(String mobileNumber);

    Page<Employee> findByIsActiveTrue(Pageable pageable);

    Long countByBranchId(Long branchId);

    @Query("""
        SELECT e FROM Employee e
        WHERE e.isActive = true
          AND (:branchId IS NULL OR e.branch.id = :branchId)
          AND (LOWER(e.fullName)     LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(e.mobileNumber) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', :query, '%')))
    """)
    Page<Employee> searchActiveScoped(@Param("branchId") Long branchId, @Param("query") String query, Pageable pageable);

    @Query("""
        SELECT e FROM Employee e
        WHERE (:branchId IS NULL OR e.branch.id = :branchId)
          AND (LOWER(e.fullName)     LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(e.mobileNumber) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', :query, '%')))
    """)
    Page<Employee> searchAllScoped(@Param("branchId") Long branchId, @Param("query") String query, Pageable pageable);

    @Query("""
        SELECT e FROM Employee e
        WHERE e.isActive = true
          AND (LOWER(e.fullName)     LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(e.mobileNumber) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', :query, '%')))
    """)
    Page<Employee> searchActive(@Param("query") String query, Pageable pageable);

    @Query("""
        SELECT e FROM Employee e
        WHERE (LOWER(e.fullName)     LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(e.mobileNumber) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', :query, '%')))
    """)
    Page<Employee> searchAll(@Param("query") String query, Pageable pageable);

    @Query("SELECT e.employeeCode FROM Employee e WHERE e.employeeCode LIKE 'EMP-%'")
    List<String> findEmployeeCodesStartingWithEmp();

    /**
     * Returns the highest numeric suffix from existing employee codes (e.g. max of EMP-001, EMP-007 → 7).
     * Safely filters out non-numeric suffixes (e.g. EMP-ADMIN, EMP-TEMP, EMP-001-A) to prevent SQL cast exceptions.
     */
    default long findMaxEmployeeCodeSequence() {
        List<String> codes = findEmployeeCodesStartingWithEmp();
        if (codes == null || codes.isEmpty()) {
            return 0L;
        }
        long maxSeq = 0L;
        Pattern pattern = Pattern.compile("^EMP-(\\d+)$");
        for (String code : codes) {
            if (code == null) continue;
            Matcher matcher = pattern.matcher(code.trim());
            if (matcher.matches()) {
                try {
                    long seq = Long.parseLong(matcher.group(1));
                    if (seq > maxSeq) {
                        maxSeq = seq;
                    }
                } catch (NumberFormatException ignored) {
                    // Ignore out-of-range or malformed numeric suffixes
                }
            }
        }
        return maxSeq;
    }
}
