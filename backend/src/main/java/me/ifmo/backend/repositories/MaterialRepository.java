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
import java.util.Collection;
import java.util.List;

public interface MaterialRepository extends JpaRepository<Material, Long> {

    Optional<Material> findByIsbn(String isbn);

    boolean existsByIsbn(String isbn);

    @Query("""
           SELECT material.id FROM Material material
               WHERE (:excludedId IS NULL OR material.id <> :excludedId)
                   AND material.status <> me.ifmo.backend.entities.enums.MaterialStatus.REMOVED
                   AND lower(material.title) = lower(:title)
                   AND material.publicationYear = :publicationYear
                   AND (SELECT count(materialAuthor)
                            FROM MaterialAuthor materialAuthor
                            WHERE materialAuthor.material = material) = :authorCount
                   AND (SELECT count(materialAuthor)
                            FROM MaterialAuthor materialAuthor
                            WHERE materialAuthor.material = material
                                AND materialAuthor.author.id IN :authorIds) = :authorCount
    """)
    List<Long> findDuplicateIds(@Param("excludedId") Long excludedId,
                                @Param("title") String title,
                                @Param("publicationYear") Integer publicationYear,
                                @Param("authorIds") Collection<Long> authorIds,
                                @Param("authorCount") long authorCount);

    Page<Material> findByStatus(MaterialStatus status, Pageable pageable);

    Page<Material> findByMaterialType(MaterialType type, Pageable pageable);

    Page<Material> findByStatusAndMaterialType(MaterialStatus status, MaterialType type, Pageable pageable);

    Page<Material> findByPublicationYear(Integer publicationYear, Pageable pageable);

    Page<Material> findByPublisherContainingIgnoreCase(String publisher, Pageable pageable);

    @Query("""
           SELECT material FROM Material material
               WHERE (:query IS NULL OR :query = ''
                   OR material.isbn LIKE concat('%', :query, '%')
                   OR material.isbn = :query
                   OR lower(material.title) LIKE lower(concat('%', :query, '%'))
                   OR lower(coalesce(material.description, '')) LIKE lower(concat('%', :query, '%'))
                   OR lower(coalesce(material.publisher, '')) LIKE lower(concat('%', :query, '%'))
                   OR EXISTS (
                       SELECT materialAuthor FROM MaterialAuthor materialAuthor
                           WHERE materialAuthor.material = material
                               AND (
                                   lower(materialAuthor.author.firstName) LIKE lower(concat('%', :query, '%'))
                                   OR lower(coalesce(materialAuthor.author.middleName, '')) LIKE lower(concat('%', :query, '%'))
                                   OR lower(materialAuthor.author.lastName) LIKE lower(concat('%', :query, '%'))
                               )
                   )
                   OR EXISTS (
                       SELECT materialGenre FROM MaterialGenre materialGenre
                           WHERE materialGenre.material = material
                               AND (
                                   lower(materialGenre.genre.code) LIKE lower(concat('%', :query, '%'))
                                   OR lower(materialGenre.genre.name) LIKE lower(concat('%', :query, '%'))
                               )
                   )
                   OR EXISTS (
                       SELECT copy FROM MaterialCopy copy
                           WHERE copy.material = material
                               AND copy.inventoryNumber = :query
                   ))
                   AND (:type IS NULL OR material.materialType = :type)
                   AND (:status IS NULL OR material.status = :status)
                   AND (:publicationYear IS NULL OR material.publicationYear = :publicationYear)
                   AND (:authorId IS NULL OR EXISTS (
                       SELECT materialAuthor FROM MaterialAuthor materialAuthor
                           WHERE materialAuthor.material = material
                               AND materialAuthor.author.id = :authorId
                   ))
                   AND (:genreId IS NULL OR EXISTS (
                       SELECT materialGenre FROM MaterialGenre materialGenre
                           WHERE materialGenre.material = material
                               AND materialGenre.genre.id = :genreId
                   ))
                   AND (:branchId IS NULL OR EXISTS (
                       SELECT copy FROM MaterialCopy copy
                           WHERE copy.material = material
                               AND copy.branch.id = :branchId
                                   AND copy.status <> me.ifmo.backend.entities.enums.CopyStatus.REMOVED \s
                   ))
   \s""")
    Page<Material> search(@Param("query") String query,
                          @Param("type") MaterialType type,
                          @Param("status") MaterialStatus status,
                          @Param("publicationYear") Integer publicationYear,
                          @Param("authorId") Long authorId,
                          @Param("genreId") Long genreId,
                          @Param("branchId") Long branchId,
                          Pageable pageable);
}
