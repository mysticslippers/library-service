package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.Material;
import me.ifmo.backend.entities.enums.MaterialStatus;
import me.ifmo.backend.entities.enums.MaterialType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MaterialRepository extends JpaRepository<Material, Long> {

    Optional<Material> findByIsbn(String isbn);

    boolean existsByIsbn(String isbn);

    Page<Material> findByStatus(MaterialStatus status, Pageable pageable);
}
