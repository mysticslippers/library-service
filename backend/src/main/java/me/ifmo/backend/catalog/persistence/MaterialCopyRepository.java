package me.ifmo.backend.catalog.persistence;

import me.ifmo.backend.catalog.domain.MaterialCopy;
import me.ifmo.backend.catalog.domain.enums.CopyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface MaterialCopyRepository extends JpaRepository<MaterialCopy, Long> {

    @Query(value = "SELECT copy.* FROM material_copies copy WHERE copy.id = :id FOR UPDATE", nativeQuery = true)
    Optional<MaterialCopy> findByIdForUpdate(@Param("id") Long id);

    @Query(value = """
            SELECT copy.* FROM material_copies copy
            WHERE copy.material_id = :materialId AND copy.branch_id = :branchId AND copy.status = 'AVAILABLE'
            ORDER BY copy.created_at, copy.id LIMIT 1 FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<MaterialCopy> findAvailableForUpdate(@Param("materialId") Long materialId, @Param("branchId") Long branchId);

    Optional<MaterialCopy> findByInventoryNumber(String inventoryNumber);

    boolean existsByInventoryNumber(String inventoryNumber);

    List<MaterialCopy> findByMaterial_Id(Long materialId);

    Page<MaterialCopy> findByMaterial_Id(Long materialId, Pageable pageable);

    Page<MaterialCopy> findByBranch_Id(Long branchId, Pageable pageable);

    Page<MaterialCopy> findByStatus(CopyStatus status, Pageable pageable);

    Page<MaterialCopy> findByMaterial_IdAndBranch_Id(Long materialId, Long branchId, Pageable pageable);

    Page<MaterialCopy> findByMaterial_IdAndStatus(Long materialId, CopyStatus status, Pageable pageable);

    boolean existsByMaterial_IdAndStatusNot(Long materialId, CopyStatus status);

    boolean existsByMaterial_IdAndStatusIn(Long materialId, Collection<CopyStatus> statuses);

    Page<MaterialCopy> findByBranch_IdAndStatus(Long branchId, CopyStatus status, Pageable pageable);

    Page<MaterialCopy> findByMaterial_IdAndBranch_IdAndStatus(Long materialId, Long branchId, CopyStatus status, Pageable pageable);

    boolean existsByBranch_IdAndStatusNot(Long branchId, CopyStatus status);

}
