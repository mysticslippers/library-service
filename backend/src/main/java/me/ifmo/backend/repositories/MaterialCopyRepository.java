package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.Material;
import me.ifmo.backend.entities.MaterialCopy;
import me.ifmo.backend.entities.enums.CopyStatus;
import me.ifmo.backend.entities.enums.MaterialStatus;
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
}
