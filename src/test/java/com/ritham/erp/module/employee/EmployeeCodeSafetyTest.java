package com.ritham.erp.module.employee;

import com.ritham.erp.module.employee.repository.EmployeeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;

class EmployeeCodeSafetyTest {

    @Test
    @DisplayName("findMaxEmployeeCodeSequence correctly identifies highest numeric sequence")
    void returnsMaxNumericSequence() {
        EmployeeRepository repo = Mockito.mock(EmployeeRepository.class);
        doReturn(Arrays.asList("EMP-001", "EMP-005", "EMP-020", "EMP-003"))
                .when(repo).findEmployeeCodesStartingWithEmp();
        Mockito.doCallRealMethod().when(repo).findMaxEmployeeCodeSequence();

        long max = repo.findMaxEmployeeCodeSequence();
        assertEquals(20L, max);
    }

    @Test
    @DisplayName("findMaxEmployeeCodeSequence safely ignores non-numeric employee codes without throwing exception")
    void safelyIgnoresNonNumericCodes() {
        EmployeeRepository repo = Mockito.mock(EmployeeRepository.class);
        doReturn(Arrays.asList("EMP-ADMIN", "EMP-MGR", "EMP-TEMP", "EMP-", "EMP-001-A", "EMP-XYZ"))
                .when(repo).findEmployeeCodesStartingWithEmp();
        Mockito.doCallRealMethod().when(repo).findMaxEmployeeCodeSequence();

        long max = repo.findMaxEmployeeCodeSequence();
        assertEquals(0L, max);
    }

    @Test
    @DisplayName("findMaxEmployeeCodeSequence correctly parses max numeric code when non-numeric codes are present")
    void handlesMixedNumericAndNonNumericCodes() {
        EmployeeRepository repo = Mockito.mock(EmployeeRepository.class);
        doReturn(Arrays.asList(
                "EMP-001",
                "EMP-ADMIN",
                "EMP-045",
                "EMP-TEMP",
                "EMP-007-OLD",
                "EMP-",
                "EMP-012",
                "EMP-999999999999999999999999999999999999" // Exceeds Long.MAX_VALUE
        )).when(repo).findEmployeeCodesStartingWithEmp();
        Mockito.doCallRealMethod().when(repo).findMaxEmployeeCodeSequence();

        long max = repo.findMaxEmployeeCodeSequence();
        assertEquals(45L, max);
    }

    @Test
    @DisplayName("findMaxEmployeeCodeSequence returns 0 when no employee codes exist or list is empty")
    void handlesEmptyOrNullResults() {
        EmployeeRepository repo = Mockito.mock(EmployeeRepository.class);
        doReturn(Collections.emptyList()).when(repo).findEmployeeCodesStartingWithEmp();
        Mockito.doCallRealMethod().when(repo).findMaxEmployeeCodeSequence();

        assertEquals(0L, repo.findMaxEmployeeCodeSequence());

        doReturn(null).when(repo).findEmployeeCodesStartingWithEmp();
        assertEquals(0L, repo.findMaxEmployeeCodeSequence());
    }
}
