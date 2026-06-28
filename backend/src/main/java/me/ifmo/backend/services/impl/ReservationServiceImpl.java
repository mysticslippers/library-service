package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.catalog.response.MaterialShortResponse;
import me.ifmo.backend.entities.Material;
import me.ifmo.backend.entities.MaterialAuthor;
import me.ifmo.backend.entities.MaterialGenre;
import me.ifmo.backend.exceptions.domain.BusinessRuleException;
import me.ifmo.backend.mappers.MaterialMapper;
import me.ifmo.backend.repositories.MaterialAuthorRepository;
import me.ifmo.backend.repositories.MaterialGenreRepository;
import me.ifmo.backend.services.ReservationService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {

    private final MaterialAuthorRepository materialAuthorRepository;
    private final MaterialGenreRepository materialGenreRepository;
    private final MaterialMapper materialMapper;

    private String normalize(String value, String fieldName) {
        if (value == null || value.strip().isBlank())
            throw new BusinessRuleException("%s must not be blank".formatted(fieldName));

        return value.strip();
    }

    private MaterialShortResponse toMaterialShortResponse(Material material) {
        List<MaterialAuthor> authors = materialAuthorRepository.findByMaterial_IdOrderByAuthorOrderAsc(material.getId());

        List<MaterialGenre> genres = materialGenreRepository.findByMaterial_Id(material.getId());

        return materialMapper.toShortResponse(material, authors, genres);
    }
}
