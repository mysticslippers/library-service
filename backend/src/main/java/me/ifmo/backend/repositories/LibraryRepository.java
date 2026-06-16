package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.Library;
import me.ifmo.backend.entities.enums.LibraryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LibraryRepository extends JpaRepository<Library, Long> {

    Optional<Library> findByCode(String code);

    boolean existsByCode(String code);
}
