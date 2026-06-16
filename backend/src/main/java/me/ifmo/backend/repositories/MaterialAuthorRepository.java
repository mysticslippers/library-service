package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.MaterialAuthor;
import me.ifmo.backend.entities.id.MaterialAuthorId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MaterialAuthorRepository extends JpaRepository<MaterialAuthor, MaterialAuthorId> {

    List<MaterialAuthor> findByMaterial_IdOrderByAuthorOrderAsc(Long materialId);

    List<MaterialAuthor> findByAuthor_Id(Long authorId);

    Optional<MaterialAuthor> findByMaterial_IdAndAuthor_Id(Long materialId, Long authorId);

    boolean existsByMaterial_IdAndAuthor_Id(Long materialId, Long authorId);

    void deleteByMaterial_Id(Long materialId);

    void deleteByMaterial_IdAndAuthor_Id(Long materialId, Long authorId);
}
