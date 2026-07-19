package me.ifmo.backend.catalog.storage;

public record StoredCoverMetadata(
        long contentLength,
        String contentType,
        String etag
) {
}
