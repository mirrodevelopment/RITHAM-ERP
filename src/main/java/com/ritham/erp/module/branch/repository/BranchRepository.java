package com.ritham.erp.module.branch.repository;

import com.ritham.erp.module.branch.entity.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BranchRepository extends JpaRepository<Branch, Long> {

    Optional<Branch> findByBranchCode(String branchCode);

    boolean existsByBranchCode(String branchCode);

    List<Branch> findAllByOrderByCreatedAtAsc();

    List<Branch> findByIsActiveTrueOrderByCreatedAtAsc();
}
