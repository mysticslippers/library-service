package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.Genre;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GenreRepository extends JpaRepository<Genre, Long> {

    Optional<Genre> findByCode(String code);

    boolean existsByCode(String code);

    Optional<Genre> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    Page<Genre> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
