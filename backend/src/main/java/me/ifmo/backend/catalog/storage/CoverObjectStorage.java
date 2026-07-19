package me.ifmo.backend.catalog.storage;

public interface CoverObjectStorage {

    StoredCoverMetadata put(String objectKey, byte[] content, String contentType);

    StoredCover get(String objectKey);

    void delete(String objectKey);

    void verifyAvailable();
}
