package me.ifmo.backend.library.persistence;

import me.ifmo.backend.library.domain.Branch;
import me.ifmo.backend.library.domain.enums.BranchStatus;
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
