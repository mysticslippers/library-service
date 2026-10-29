package me.ifmo.backend.library.persistence;

import me.ifmo.backend.library.domain.Library;
import me.ifmo.backend.library.domain.enums.LibraryStatus;
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
            WHERE library.status = COALESCE(:status, library.status)
              AND (
                  CAST(:query AS string) IS NULL
                  OR LOWER(library.name) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%'))
                  OR LOWER(library.code) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%'))
              )
    """)
    Page<Library> search(@Param("query") String query,
                         @Param("status") LibraryStatus status,
                         Pageable pageable);
}
