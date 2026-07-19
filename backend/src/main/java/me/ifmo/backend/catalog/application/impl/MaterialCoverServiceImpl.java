package me.ifmo.backend.catalog.application.impl;

import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.ifmo.backend.catalog.application.MaterialCoverService;
import me.ifmo.backend.catalog.application.cover.CoverImageValidator;
import me.ifmo.backend.catalog.application.cover.MaterialCoverContent;
import me.ifmo.backend.catalog.domain.Material;
import me.ifmo.backend.catalog.domain.enums.MaterialStatus;
import me.ifmo.backend.catalog.mapper.MaterialCoverUrlFactory;
import me.ifmo.backend.catalog.persistence.MaterialRepository;
import me.ifmo.backend.catalog.storage.CoverObjectStorage;
import me.ifmo.backend.catalog.web.response.MaterialCoverResponse;
import me.ifmo.backend.shared.cache.InvalidateCatalogSearch;
import me.ifmo.backend.shared.error.BusinessRuleException;
import me.ifmo.backend.shared.error.ResourceNotFoundException;
import me.ifmo.backend.shared.observability.LoggableOperation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialCoverServiceImpl implements MaterialCoverService {

    private final MaterialRepository materialRepository;
    private final CoverObjectStorage storage;
    private final CoverImageValidator imageValidator;
    private final MaterialCoverUrlFactory coverUrlFactory;

    @Override
    @Transactional
    @InvalidateCatalogSearch
    @LoggableOperation("material-cover.upload")
    @Observed(
            name = "library.operation",
            contextualName = "material-cover.upload",
            lowCardinalityKeyValues = {"domain", "catalog", "operation", "material-cover.upload"}
    )
    public MaterialCoverResponse upload(Long materialId, MultipartFile file) {
        Material material = findMaterial(materialId);
        if (material.getStatus() == MaterialStatus.ARCHIVED || material.getStatus() == MaterialStatus.REMOVED)
            throw new BusinessRuleException("Archived or removed material cannot receive a cover");

        var cover = imageValidator.validate(file);
        UUID version = UUID.randomUUID();
        String newObjectKey = "materials/%d/covers/%s.%s"
                .formatted(materialId, version, cover.extension());
        String oldObjectKey = material.getCoverObjectKey();
        boolean cleanupScheduled = false;

        try {
            var stored = storage.put(newObjectKey, cover.content(), cover.contentType());
            material.setCoverObjectKey(newObjectKey);
            material.setCoverVersion(version);
            materialRepository.saveAndFlush(material);
            scheduleReplacementCleanup(newObjectKey, oldObjectKey);
            cleanupScheduled = true;

            return new MaterialCoverResponse(
                    coverUrlFactory.create(materialId, version),
                    stored.contentType(),
                    stored.contentLength(),
                    stored.etag()
            );
        } catch (RuntimeException exception) {
            if (!cleanupScheduled)
                safeDelete(newObjectKey);
            throw exception;
        }
    }

    @Override
    @Transactional(readOnly = true)
    @Observed(
            name = "library.operation",
            contextualName = "material-cover.get",
            lowCardinalityKeyValues = {"domain", "catalog", "operation", "material-cover.get"}
    )
    public MaterialCoverContent getPublic(Long materialId) {
        Material material = findMaterial(materialId);
        if (material.getStatus() != MaterialStatus.ACTIVE || material.getCoverObjectKey() == null)
            throw coverNotFound(materialId);

        var stored = storage.get(material.getCoverObjectKey());
        return new MaterialCoverContent(
                stored.content(),
                stored.contentLength(),
                stored.contentType(),
                stored.etag(),
                stored.lastModified()
        );
    }

    @Override
    @Transactional
    @InvalidateCatalogSearch
    @LoggableOperation("material-cover.delete")
    @Observed(
            name = "library.operation",
            contextualName = "material-cover.delete",
            lowCardinalityKeyValues = {"domain", "catalog", "operation", "material-cover.delete"}
    )
    public void delete(Long materialId) {
        Material material = findMaterial(materialId);
        String objectKey = material.getCoverObjectKey();
        if (objectKey == null)
            return;

        material.setCoverObjectKey(null);
        material.setCoverVersion(null);
        materialRepository.saveAndFlush(material);
        scheduleDeleteAfterCommit(objectKey);
    }

    private Material findMaterial(Long materialId) {
        return materialRepository.findById(materialId).orElseThrow(
                () -> new ResourceNotFoundException(
                        "Material with id '%s' not found".formatted(materialId)));
    }

    private ResourceNotFoundException coverNotFound(Long materialId) {
        return new ResourceNotFoundException(
                "Cover for material with id '%s' not found".formatted(materialId));
    }

    private void scheduleReplacementCleanup(String newObjectKey, String oldObjectKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            safeDelete(oldObjectKey);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED)
                    safeDelete(oldObjectKey);
                else
                    safeDelete(newObjectKey);
            }
        });
    }

    private void scheduleDeleteAfterCommit(String objectKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            safeDelete(objectKey);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                safeDelete(objectKey);
            }
        });
    }

    private void safeDelete(String objectKey) {
        if (objectKey == null)
            return;

        try {
            storage.delete(objectKey);
        } catch (RuntimeException exception) {
            log.warn("Unable to clean up cover object '{}'", objectKey, exception);
        }
    }
}
