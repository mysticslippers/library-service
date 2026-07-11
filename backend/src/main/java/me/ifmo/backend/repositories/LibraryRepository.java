package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.Library;
import me.ifmo.backend.entities.enums.LibraryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LibraryRepository extends JpaRepository<Library, Long> {

    Optional<Library> findByCode(String code);

    boolean existsByCode(String code);

    Page<Library> findByStatus(LibraryStatus status, Pageable pageable);

    @Query("""
        SELECT library FROM Library library
            WHERE (:status IS NULL OR library.status = :status)
              AND (
                  :query IS NULL
                  OR LOWER(library.name) LIKE LOWER(CONCAT('%', :query, '%'))
                  OR LOWER(library.code) LIKE LOWER(CONCAT('%', :query, '%'))
              )
    """)
    Page<Library> search(@Param("query") String query,
                         @Param("status") LibraryStatus status,
                         Pageable pageable);
}
