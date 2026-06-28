package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.catalog.request.CreateMaterialRequest;
import me.ifmo.backend.dto.catalog.response.MaterialResponse;
import me.ifmo.backend.entities.Material;
import me.ifmo.backend.exceptions.domain.BusinessRuleException;
import me.ifmo.backend.exceptions.domain.DuplicateResourceException;
import me.ifmo.backend.mappers.MaterialMapper;
import me.ifmo.backend.repositories.MaterialRepository;
import me.ifmo.backend.services.MaterialService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MaterialServiceImpl implements MaterialService {

    private final MaterialRepository repository;
    private final MaterialMapper mapper;

    private String normalize(String value, String fieldName){
        String normalized;

        if(fieldName.equals("Title")){
            normalized = value.strip();

            if (normalized.isBlank())
                throw new BusinessRuleException("%s must not be blank".formatted(fieldName));

            return normalized;
        } else {
            if(value == null)
                return null;

            normalized = value.strip();
            return normalized.isBlank() ? null : normalized;
        }
    }
}
