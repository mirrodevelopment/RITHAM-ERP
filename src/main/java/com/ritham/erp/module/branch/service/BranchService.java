package com.ritham.erp.module.branch.service;

import com.ritham.erp.common.exception.AppException;
import com.ritham.erp.common.exception.ErrorCode;
import com.ritham.erp.module.branch.dto.*;
import com.ritham.erp.module.branch.entity.Branch;
import com.ritham.erp.module.branch.repository.BranchRepository;
import com.ritham.erp.module.employee.repository.EmployeeRepository;
import com.ritham.erp.module.order.repository.CustomerOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BranchService {

    private final BranchRepository branchRepository;
    private final BranchMapper branchMapper;
    private final EmployeeRepository employeeRepository;
    private final CustomerOrderRepository customerOrderRepository;

    @Transactional(readOnly = true)
    public List<BranchResponse> getAllBranches(boolean activeOnly) {
        List<Branch> branches = activeOnly
                ? branchRepository.findByIsActiveTrueOrderByCreatedAtAsc()
                : branchRepository.findAllByOrderByCreatedAtAsc();

        return branches.stream().map(b -> {
            Long empCount = employeeRepository.countByBranchId(b.getId());
            Long orderCount = customerOrderRepository.countByBranchId(b.getId());
            return branchMapper.toResponse(b, empCount, orderCount);
        }).toList();
    }

    @Transactional(readOnly = true)
    public BranchResponse getBranchById(Long id) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Branch not found"));
        Long empCount = employeeRepository.countByBranchId(branch.getId());
        Long orderCount = customerOrderRepository.countByBranchId(branch.getId());
        return branchMapper.toResponse(branch, empCount, orderCount);
    }

    @Transactional
    public BranchResponse createBranch(CreateBranchRequest request) {
        String code = request.getBranchCode().trim().toUpperCase();
        if (branchRepository.existsByBranchCode(code)) {
            throw new AppException(ErrorCode.DUPLICATE_RESOURCE, "Branch with code " + code + " already exists");
        }

        Branch branch = Branch.builder()
                .branchCode(code)
                .name(request.getName().trim())
                .city(request.getCity() != null ? request.getCity().trim() : null)
                .address(request.getAddress() != null ? request.getAddress().trim() : null)
                .phone(request.getPhone() != null ? request.getPhone().trim() : null)
                .email(request.getEmail() != null ? request.getEmail().trim() : null)
                .gstNumber(request.getGstNumber() != null ? request.getGstNumber().trim() : null)
                .isActive(true)
                .build();

        Branch saved = branchRepository.save(branch);
        log.info("Created new company branch: {} ({})", saved.getName(), saved.getBranchCode());
        return branchMapper.toResponse(saved, 0L, 0L);
    }

    @Transactional
    public BranchResponse updateBranch(Long id, UpdateBranchRequest request) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Branch not found"));

        branch.setName(request.getName().trim());
        branch.setCity(request.getCity() != null ? request.getCity().trim() : null);
        branch.setAddress(request.getAddress() != null ? request.getAddress().trim() : null);
        branch.setPhone(request.getPhone() != null ? request.getPhone().trim() : null);
        branch.setEmail(request.getEmail() != null ? request.getEmail().trim() : null);
        branch.setGstNumber(request.getGstNumber() != null ? request.getGstNumber().trim() : null);
        if (request.getIsActive() != null) {
            branch.setIsActive(request.getIsActive());
        }

        Branch saved = branchRepository.save(branch);
        Long empCount = employeeRepository.countByBranchId(saved.getId());
        Long orderCount = customerOrderRepository.countByBranchId(saved.getId());
        return branchMapper.toResponse(saved, empCount, orderCount);
    }

    @Transactional
    public void toggleBranchStatus(Long id) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Branch not found"));
        branch.setIsActive(!branch.getIsActive());
        branchRepository.save(branch);
    }
}
