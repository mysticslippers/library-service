package me.ifmo.backend.catalog.application.search;

import me.ifmo.backend.catalog.domain.enums.MaterialStatus;
import me.ifmo.backend.catalog.domain.enums.MaterialType;
import org.springframework.data.domain.Pageable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.stream.Collectors;

public record MaterialSearchCriteria(
        CatalogVisibility visibility,
        String query,
        MaterialType materialType,
        MaterialStatus status,
        Integer publicationYear,
        Long authorId,
        Long genreId,
        Long branchId,
        Pageable pageable
) {

    private static final String KEY_VERSION = "v1";
    private static final int MAX_CACHED_PAGE_NUMBER = 9;
    private static final int MAX_CACHED_PAGE_SIZE = 100;

    public MaterialSearchCriteria {
        Objects.requireNonNull(visibility, "visibility must not be null");
        Objects.requireNonNull(pageable, "pageable must not be null");
        query = query == null ? "" : query.strip();
    }

    public boolean cacheable() {
        return pageable.isPaged() && pageable.getPageNumber() <= MAX_CACHED_PAGE_NUMBER && pageable.getPageSize() <= MAX_CACHED_PAGE_SIZE;
    }

    public boolean includeRemovedCopies() {
        return visibility == CatalogVisibility.STAFF;
    }

    public String cacheKey() {
        int pageNumber = (pageable.isPaged()) ? pageable.getPageNumber() : 0;
        int pageSize = (pageable.isPaged()) ? pageable.getPageSize() : Integer.MAX_VALUE;
        String digest = sha256(canonicalValue(pageNumber, pageSize));

        return "%s:%s:p%d:s%d:%s".formatted(KEY_VERSION, visibility, pageNumber, pageSize, digest);
    }

    private String canonicalValue(int pageNumber, int pageSize) {
        String sort = pageable.getSort().stream().map(order -> "%s,%s,%s,%s".formatted(
                        order.getProperty(),
                        order.getDirection(),
                        order.isIgnoreCase(),
                        order.getNullHandling()
                )).collect(Collectors.joining(";"));

        return String.join("|", KEY_VERSION, visibility.name(), query, stringValue(materialType), stringValue(status),
                stringValue(publicationYear), stringValue(authorId), stringValue(genreId), stringValue(branchId),
                Integer.toString(pageNumber), Integer.toString(pageSize), sort);
    }

    private String stringValue(Object value) {
        return value == null ? "<null>" : value.toString();
    }

    private String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
