package me.ifmo.backend.catalog.application.cover;

import java.io.InputStream;
import java.time.Instant;

public record MaterialCoverContent(
        InputStream content,
        long contentLength,
        String contentType,
        String etag,
        Instant lastModified
) {
}
