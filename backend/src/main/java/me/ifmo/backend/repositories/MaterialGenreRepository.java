package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.MaterialGenre;
import me.ifmo.backend.entities.id.MaterialGenreId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MaterialGenreRepository extends JpaRepository<MaterialGenre, MaterialGenreId> {

    List<MaterialGenre> findByMaterial_Id(Long materialId);

    List<MaterialGenre> findByGenre_Id(Long genreId);

    boolean existsByGenre_Id(Long genreId);

    Optional<MaterialGenre> findByMaterial_IdAndGenre_Id(Long materialId, Long genreId);

    boolean existsByMaterial_IdAndGenre_Id(Long materialId, Long genreId);

    void deleteByMaterial_Id(Long materialId);

    void deleteByMaterial_IdAndGenre_Id(Long materialId, Long genreId);
}
