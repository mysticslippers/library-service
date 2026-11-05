package me.ifmo.backend.catalog.application.impl;

import me.ifmo.backend.catalog.application.MaterialLocks;
import me.ifmo.backend.catalog.application.cover.CoverCleanupService;
import me.ifmo.backend.catalog.application.cover.CoverImageValidator;
import me.ifmo.backend.catalog.application.cover.ValidatedCover;
import me.ifmo.backend.catalog.domain.Material;
import me.ifmo.backend.catalog.domain.enums.MaterialStatus;
import me.ifmo.backend.catalog.mapper.MaterialCoverUrlFactory;
import me.ifmo.backend.catalog.persistence.MaterialRepository;
import me.ifmo.backend.catalog.storage.CoverObjectStorage;
import me.ifmo.backend.catalog.storage.StoredCover;
import me.ifmo.backend.catalog.storage.StoredCoverMetadata;
import me.ifmo.backend.shared.exceptions.BusinessRuleException;
import me.ifmo.backend.shared.exceptions.ObjectStorageException;
import me.ifmo.backend.shared.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
@DisplayName("Material cover service")
class MaterialCoverServiceImplTest {

    private static final long MATERIAL_ID = 7L;

    @Mock
    private MaterialRepository materialRepository;
    @Mock
    private MaterialLocks materialLocks;
    @Mock
    private CoverCleanupService cleanup;
    @Mock
    private CoverObjectStorage storage;
    @Mock
    private CoverImageValidator imageValidator;
    @Mock
    private MaterialCoverUrlFactory coverUrlFactory;
    @Mock
    private TransactionTemplate transactions;

    @InjectMocks
    private MaterialCoverServiceImpl service;

    @BeforeEach
    void executeTransactionCallbacks() {
        lenient().when(transactions.execute(any())).thenAnswer(call ->
                ((TransactionCallback<?>) call.getArgument(0)).doInTransaction(mock(TransactionStatus.class)));
    }

    @Test
    @DisplayName("uploads a versioned cover and queues the previous object for cleanup")
    void uploadsVersionedCoverAndRemovesPreviousObject() {
        var material = activeMaterial();
        material.setCoverObjectKey("materials/7/covers/old.png");
        byte[] content = {(byte) 0x89, 0x50};
        var file = new MockMultipartFile("file", content);
        when(materialRepository.findById(MATERIAL_ID)).thenReturn(Optional.of(material));
        when(materialLocks.lock(MATERIAL_ID)).thenReturn(material);
        when(imageValidator.validate(file)).thenReturn(new ValidatedCover(content, "image/png", "png"));
        when(storage.put(anyString(), eq(content), eq("image/png")))
                .thenReturn(new StoredCoverMetadata(content.length, "image/png", "\"etag\""));
        when(coverUrlFactory.create(eq(MATERIAL_ID), org.mockito.ArgumentMatchers.any()))
                .thenReturn("/api/materials/7/cover?v=new");

        var response = service.upload(MATERIAL_ID, file);

        var key = ArgumentCaptor.forClass(String.class);
        verify(storage).put(key.capture(), eq(content), eq("image/png"));
        assertThat(key.getValue())
                .startsWith("materials/7/covers/")
                .endsWith(".png");
        assertThat(material.getCoverObjectKey()).isEqualTo(key.getValue());
        assertThat(material.getCoverVersion()).isNotNull();
        verify(materialRepository).saveAndFlush(material);
        verify(cleanup).enqueue("materials/7/covers/old.png");
        assertThat(response.coverUrl()).isEqualTo("/api/materials/7/cover?v=new");
    }

    @Test
    @DisplayName("cleans up a new object when the database update fails")
    void cleansUpNewObjectWhenDatabaseUpdateFails() {
        var material = activeMaterial();
        byte[] content = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
        var file = new MockMultipartFile("file", content);
        when(materialRepository.findById(MATERIAL_ID)).thenReturn(Optional.of(material));
        when(materialLocks.lock(MATERIAL_ID)).thenReturn(material);
        when(imageValidator.validate(file)).thenReturn(new ValidatedCover(content, "image/jpeg", "jpg"));
        when(storage.put(anyString(), eq(content), eq("image/jpeg")))
                .thenReturn(new StoredCoverMetadata(content.length, "image/jpeg", "\"etag\""));
        when(materialRepository.saveAndFlush(material)).thenThrow(new IllegalStateException("database failure"));

        assertThatThrownBy(() -> service.upload(MATERIAL_ID, file))
                .isInstanceOf(IllegalStateException.class);

        var deletedKey = ArgumentCaptor.forClass(String.class);
        verify(cleanup).registerUpload(deletedKey.capture());
        verify(cleanup, never()).completeUpload(anyString());
        verify(storage, never()).delete(anyString());
        assertThat(deletedKey.getValue()).startsWith("materials/7/covers/");
    }

    @Test
    @DisplayName("rejects uploads for archived materials")
    void rejectsUploadForArchivedMaterial() {
        var material = activeMaterial();
        material.setStatus(MaterialStatus.ARCHIVED);
        var file = new MockMultipartFile("file", new byte[]{1});
        when(materialRepository.findById(MATERIAL_ID)).thenReturn(Optional.of(material));

        assertThatThrownBy(() -> service.upload(MATERIAL_ID, file))
                .isInstanceOf(BusinessRuleException.class);
        verify(imageValidator, never()).validate(file);
        verify(storage, never()).put(anyString(), org.mockito.ArgumentMatchers.any(), anyString());
    }

    @Test
    @DisplayName("streams the cover of an active material")
    void streamsActiveMaterialCover() {
        var material = activeMaterial();
        material.setCoverObjectKey("materials/7/covers/cover.webp");
        var version = java.util.UUID.randomUUID();
        material.setCoverVersion(version);
        byte[] content = {1, 2, 3};
        var stored = new StoredCover(
                new ByteArrayInputStream(content),
                content.length,
                "image/webp",
                "\"etag\"",
                Instant.parse("2026-01-01T00:00:00Z")
        );
        when(materialLocks.lockForRead(MATERIAL_ID)).thenReturn(material);
        when(storage.get(material.getCoverObjectKey())).thenReturn(stored);

        var result = service.getPublic(MATERIAL_ID, version);

        assertThat(result.contentType()).isEqualTo("image/webp");
        assertThat(result.contentLength()).isEqualTo(3);
        assertThat(result.etag()).isEqualTo("\"etag\"");
    }

    @Test
    void rejectsAnObsoleteVersionBeforeOpeningStorage() {
        var material = activeMaterial();
        material.setCoverObjectKey("materials/7/covers/current.jpg");
        material.setCoverVersion(java.util.UUID.randomUUID());
        when(materialLocks.lockForRead(MATERIAL_ID)).thenReturn(material);

        assertThatThrownBy(() -> service.getPublic(MATERIAL_ID, java.util.UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(storage, never()).get(anyString());
    }

    @Test
    @DisplayName("hides covers of non-active materials")
    void hidesNonActiveMaterialCover() {
        var material = activeMaterial();
        material.setStatus(MaterialStatus.HIDDEN);
        material.setCoverObjectKey("materials/7/covers/cover.png");
        when(materialLocks.lockForRead(MATERIAL_ID)).thenReturn(material);

        assertThatThrownBy(() -> service.getPublic(MATERIAL_ID, null))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(storage, never()).get(anyString());
    }

    @Test
    @DisplayName("deletes the database reference and queues the object for cleanup")
    void deletesCoverReferenceAndObject() {
        var material = activeMaterial();
        material.setCoverObjectKey("materials/7/covers/cover.png");
        material.setCoverVersion(java.util.UUID.randomUUID());
        when(materialLocks.lock(MATERIAL_ID)).thenReturn(material);

        service.delete(MATERIAL_ID);

        assertThat(material.getCoverObjectKey()).isNull();
        assertThat(material.getCoverVersion()).isNull();
        verify(materialRepository).saveAndFlush(material);
        verify(cleanup).enqueue("materials/7/covers/cover.png");
    }

    @Test
    @DisplayName("propagates object storage failures")
    void propagatesStorageFailure() {
        var material = activeMaterial();
        byte[] content = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
        var file = new MockMultipartFile("file", content);
        when(materialRepository.findById(MATERIAL_ID)).thenReturn(Optional.of(material));
        when(materialLocks.lock(MATERIAL_ID)).thenReturn(material);
        when(imageValidator.validate(file)).thenReturn(new ValidatedCover(content, "image/jpeg", "jpg"));
        when(storage.put(anyString(), eq(content), eq("image/jpeg")))
                .thenThrow(new ObjectStorageException("Unavailable", new IllegalStateException()));

        assertThatThrownBy(() -> service.upload(MATERIAL_ID, file))
                .isInstanceOf(ObjectStorageException.class);
    }

    private Material activeMaterial() {
        return Material.builder()
                .id(MATERIAL_ID)
                .title("Clean Code")
                .status(MaterialStatus.ACTIVE)
                .build();
    }
}
