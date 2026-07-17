package me.ifmo.backend.catalog.persistence;

import me.ifmo.backend.catalog.domain.MaterialAuthor;
import me.ifmo.backend.catalog.domain.id.MaterialAuthorId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaterialAuthorRepository extends JpaRepository<MaterialAuthor, MaterialAuthorId> {

    List<MaterialAuthor> findByMaterial_IdOrderByAuthorOrderAsc(Long materialId);

    void deleteByMaterial_Id(Long materialId);

}