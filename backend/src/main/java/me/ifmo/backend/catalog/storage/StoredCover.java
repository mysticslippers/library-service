package me.ifmo.backend.catalog.storage;

import java.io.InputStream;
import java.time.Instant;

public record StoredCover(
        InputStream content,
        long contentLength,
        String contentType,
        String etag,
        Instant lastModified
) {
}
