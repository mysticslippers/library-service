package me.ifmo.backend.catalog.application.search;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.catalog.domain.Material;
import me.ifmo.backend.catalog.domain.enums.CopyStatus;
import me.ifmo.backend.catalog.mapper.MaterialMapper;
import me.ifmo.backend.catalog.persistence.MaterialAuthorRepository;
import me.ifmo.backend.catalog.persistence.MaterialCopyRepository;
import me.ifmo.backend.catalog.persistence.MaterialGenreRepository;
import me.ifmo.backend.catalog.web.response.MaterialResponse;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MaterialResponseAssembler {

    private final MaterialAuthorRepository materialAuthorRepository;
    private final MaterialGenreRepository materialGenreRepository;
    private final MaterialCopyRepository materialCopyRepository;
    private final MaterialMapper materialMapper;

    public MaterialResponse toResponse(Material material, boolean includeRemovedCopies) {
        var authors = materialAuthorRepository.findByMaterial_IdOrderByAuthorOrderAsc(material.getId());
        var genres = materialGenreRepository.findByMaterial_Id(material.getId());

        var copies = materialCopyRepository.findByMaterial_Id(material.getId()).stream()
                .filter(copy -> includeRemovedCopies || copy.getStatus() != CopyStatus.REMOVED).toList();

        long availableCopies = copies.stream().filter(copy -> copy.getStatus() == CopyStatus.AVAILABLE).count();

        return materialMapper.toResponse(material, authors, genres, copies, copies.size(), availableCopies);
    }
}
