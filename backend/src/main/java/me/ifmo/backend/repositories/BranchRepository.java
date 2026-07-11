package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.Branch;
import me.ifmo.backend.entities.enums.BranchStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    Page<Branch> findByLibrary_Id(Long libraryId, Pageable pageable);

    Page<Branch> findByStatus(BranchStatus status, Pageable pageable);

    Page<Branch> findByLibrary_IdAndStatus(Long libraryId, BranchStatus status, Pageable pageable);

    boolean existsByLibrary_IdAndStatusNot(Long libraryId, BranchStatus status);

    boolean existsByLibrary_IdAndNameIgnoreCase(Long libraryId, String name);
}
