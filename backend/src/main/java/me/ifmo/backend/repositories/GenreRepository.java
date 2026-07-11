package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.Genre;
import me.ifmo.backend.entities.enums.GenreStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GenreRepository extends JpaRepository<Genre, Long> {

    Optional<Genre> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByNameIgnoreCase(String name);

    @Query("""
           SELECT genre FROM Genre genre
               WHERE (:query IS NULL OR :query = ''
                   OR lower(genre.code) LIKE lower(concat('%', :query, '%'))
                   OR lower(genre.name) LIKE lower(concat('%', :query, '%')))
                   AND genre.status = :status
    """)
    Page<Genre> search(@Param("query") String query, @Param("status") GenreStatus status, Pageable pageable);
}
