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

    Page<Material> findByMaterialType(MaterialType type, Pageable pageable);

    Page<Material> findByStatusAndMaterialType(MaterialStatus status, MaterialType type, Pageable pageable);

    Page<Material> findByPublicationYear(Integer publicationYear, Pageable pageable);

    Page<Material> findByPublisherContainingIgnoreCase(String publisher, Pageable pageable);

    @Query("""
           SELECT material FROM Material material
               WHERE (:query IS NULL OR :query = ''
                   OR material.isbn LIKE concat('%', :query, '%')
                   OR lower(material.title) LIKE lower(concat('%', :query, '%'))
                   OR lower(coalesce(material.description, '')) LIKE lower(concat('%', :query, '%'))
                   OR lower(coalesce(material.publisher, '')) LIKE lower(concat('%', :query, '%')))
                   AND (:type IS NULL OR material.materialType = :type)
                   AND (:status IS NULL OR material.status = :status)
                   AND (:publicationYear IS NULL OR material.publicationYear = :publicationYear)
    """)
    Page<Material> search(@Param("query") String query,
                          @Param("type") MaterialType type,
                          @Param("status") MaterialStatus status,
                          @Param("publicationYear") Integer publicationYear,
                          Pageable pageable);
}
