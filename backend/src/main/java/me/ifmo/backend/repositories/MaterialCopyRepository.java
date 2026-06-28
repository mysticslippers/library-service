package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.MaterialCopy;
import me.ifmo.backend.entities.enums.CopyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MaterialCopyRepository extends JpaRepository<MaterialCopy, Long> {

    Optional<MaterialCopy> findByInventoryNumber(String inventoryNumber);

    boolean existsByInventoryNumber(String inventoryNumber);

    List<MaterialCopy> findByMaterial_Id(Long materialId);

    Page<MaterialCopy> findByMaterial_Id(Long materialId, Pageable pageable);

    Page<MaterialCopy> findByBranch_Id(Long branchId, Pageable pageable);

    Page<MaterialCopy> findByStatus(CopyStatus status, Pageable pageable);

    Page<MaterialCopy> findByMaterial_IdAndBranch_Id(Long materialId, Long branchId, Pageable pageable);

    Page<MaterialCopy> findByMaterial_IdAndStatus(Long materialId, CopyStatus status, Pageable pageable);

    boolean existsByMaterial_IdAndStatusNot(Long materialId, CopyStatus status);

    Page<MaterialCopy> findByBranch_IdAndStatus(Long branchId, CopyStatus status, Pageable pageable);

    Page<MaterialCopy> findByMaterial_IdAndBranch_IdAndStatus(Long materialId, Long branchId, CopyStatus status, Pageable pageable);

    boolean existsByBranch_IdAndStatusNot(Long branchId, CopyStatus status);

    List<MaterialCopy> findByMaterial_IdAndBranch_IdAndStatus(Long materialId, Long branchId, CopyStatus status);

    Optional<MaterialCopy> findFirstByMaterial_IdAndBranch_IdAndStatusOrderByCreatedAtAsc(Long materialId, Long branchId, CopyStatus status);
}
