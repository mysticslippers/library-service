package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.MaterialAuthor;
import me.ifmo.backend.entities.id.MaterialAuthorId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaterialAuthorRepository extends JpaRepository<MaterialAuthor, MaterialAuthorId> {

    List<MaterialAuthor> findByMaterial_IdOrderByAuthorOrderAsc(Long materialId);

    void deleteByMaterial_Id(Long materialId);

}