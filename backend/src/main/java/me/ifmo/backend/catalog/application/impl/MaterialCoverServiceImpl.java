package me.ifmo.backend.catalog.application.impl;

import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.catalog.application.MaterialCoverService;
import me.ifmo.backend.catalog.application.MaterialLocks;
import me.ifmo.backend.catalog.application.cover.CoverCleanupService;
import me.ifmo.backend.catalog.application.cover.CoverImageValidator;
import me.ifmo.backend.catalog.application.cover.MaterialCoverContent;
import me.ifmo.backend.catalog.application.cover.ValidatedCover;
import me.ifmo.backend.catalog.domain.Material;
import me.ifmo.backend.catalog.domain.enums.MaterialStatus;
import me.ifmo.backend.catalog.mapper.MaterialCoverUrlFactory;
import me.ifmo.backend.catalog.persistence.MaterialRepository;
import me.ifmo.backend.catalog.storage.CoverObjectStorage;
import me.ifmo.backend.catalog.web.response.MaterialCoverResponse;
import me.ifmo.backend.shared.cache.InvalidateCatalogSearch;
import me.ifmo.backend.shared.exceptions.BusinessRuleException;
import me.ifmo.backend.shared.exceptions.ResourceNotFoundException;
import me.ifmo.backend.shared.observability.LoggableOperation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MaterialCoverServiceImpl implements MaterialCoverService {

    private final MaterialRepository materialRepository;
    private final MaterialLocks materialLocks;
    private final CoverCleanupService cleanup;
    private final CoverObjectStorage storage;
    private final CoverImageValidator imageValidator;
    private final MaterialCoverUrlFactory coverUrlFactory;
    private final TransactionTemplate transactions;

    @Override
    @InvalidateCatalogSearch
    @LoggableOperation("material-cover.upload")
    @Observed(
            name = "library.operation",
            contextualName = "material-cover.upload",
            lowCardinalityKeyValues = {"domain", "catalog", "operation", "material-cover.upload"}
    )
    public MaterialCoverResponse upload(Long materialId, MultipartFile file) {
        Material material = materialRepository.findById(materialId).orElseThrow(
                () -> new ResourceNotFoundException("Material with id '%s' not found".formatted(materialId)));
        validateUploadAllowed(material);

        var cover = imageValidator.validate(file);
        UUID version = UUID.randomUUID();
        String newObjectKey = "materials/%d/covers/%s.%s".formatted(materialId, version, cover.extension());
        cleanup.registerUpload(newObjectKey);
        return transactions.execute(status -> replaceCover(materialId, cover, version, newObjectKey));
    }

    private MaterialCoverResponse replaceCover(Long materialId, ValidatedCover cover, UUID version, String newObjectKey) {
        Material material = materialLocks.lock(materialId);
        validateUploadAllowed(material);

        String oldObjectKey = material.getCoverObjectKey();
        cleanup.lockUpload(newObjectKey);

        var stored = storage.put(newObjectKey, cover.content(), cover.contentType());
        material.setCoverObjectKey(newObjectKey);
        material.setCoverVersion(version);
        materialRepository.saveAndFlush(material);
        cleanup.enqueue(oldObjectKey);
        cleanup.completeUpload(newObjectKey);

        return new MaterialCoverResponse(
                coverUrlFactory.create(materialId, version),
                stored.contentType(),
                stored.contentLength(),
                stored.etag()
        );
    }

    @Override
    @Transactional
    @Observed(
            name = "library.operation",
            contextualName = "material-cover.get",
            lowCardinalityKeyValues = {"domain", "catalog", "operation", "material-cover.get"}
    )
    public MaterialCoverContent getPublic(Long materialId, UUID requestedVersion) {
        Material material = materialLocks.lockForRead(materialId);
        if (material.getStatus() != MaterialStatus.ACTIVE || material.getCoverObjectKey() == null || (requestedVersion != null && !requestedVersion.equals(material.getCoverVersion())))
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
        Material material = materialLocks.lock(materialId);
        String objectKey = material.getCoverObjectKey();
        if (objectKey == null)
            return;

        material.setCoverObjectKey(null);
        material.setCoverVersion(null);
        materialRepository.saveAndFlush(material);
        cleanup.enqueue(objectKey);
    }

    private ResourceNotFoundException coverNotFound(Long materialId) {
        return new ResourceNotFoundException(
                "Cover for material with id '%s' not found".formatted(materialId));
    }

    private void validateUploadAllowed(Material material) {
        if (material.getStatus() == MaterialStatus.ARCHIVED || material.getStatus() == MaterialStatus.REMOVED)
            throw new BusinessRuleException("Archived or removed material cannot receive a cover");
    }
}
