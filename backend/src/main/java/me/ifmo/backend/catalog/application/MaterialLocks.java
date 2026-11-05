package me.ifmo.backend.catalog.application;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.catalog.domain.Material;
import me.ifmo.backend.catalog.persistence.MaterialRepository;
import me.ifmo.backend.shared.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class MaterialLocks {

    private final MaterialRepository repository;
    private final EntityManager entityManager;

    public Material lock(Long id) {
        Material material = repository.findByIdForUpdate(id).orElseThrow(
                () -> new ResourceNotFoundException("Material with id '%s' not found".formatted(id)));

        entityManager.refresh(material);
        return material;
    }

    public Material lockForRead(Long id) {
        Material material = repository.findByIdForRead(id).orElseThrow(
                () -> new ResourceNotFoundException("Material with id '%s' not found".formatted(id)));

        entityManager.refresh(material);
        return material;
    }
}
