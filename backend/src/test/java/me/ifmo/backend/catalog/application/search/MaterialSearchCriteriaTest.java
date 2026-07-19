package me.ifmo.backend.catalog.application.search;

import me.ifmo.backend.catalog.domain.enums.MaterialStatus;
import me.ifmo.backend.catalog.domain.enums.MaterialType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Material search cache criteria")
class MaterialSearchCriteriaTest {

    @Test
    @DisplayName("produces the same key for equivalent normalized criteria")
    void producesSameKeyForEquivalentNormalizedCriteria() {
        var first = criteria(CatalogVisibility.PUBLIC, "  clean code  ", PageRequest.of(0, 20));
        var second = criteria(CatalogVisibility.PUBLIC, "clean code", PageRequest.of(0, 20));

        assertThat(first.cacheKey()).isEqualTo(second.cacheKey());
    }

    @Test
    @DisplayName("separates public and staff search results")
    void separatesPublicAndStaffSearchResults() {
        var publicCriteria = criteria(CatalogVisibility.PUBLIC, "clean code", PageRequest.of(0, 20));
        var staffCriteria = criteria(CatalogVisibility.STAFF, "clean code", PageRequest.of(0, 20));

        assertThat(publicCriteria.cacheKey()).isNotEqualTo(staffCriteria.cacheKey());
        assertThat(publicCriteria.includeRemovedCopies()).isFalse();
        assertThat(staffCriteria.includeRemovedCopies()).isTrue();
    }

    @Test
    @DisplayName("includes pagination and sorting in the cache key")
    void includesPaginationAndSortingInCacheKey() {
        var firstPage = criteria(
                CatalogVisibility.PUBLIC,
                "clean code",
                PageRequest.of(0, 20, Sort.by("title").ascending())
        );
        var secondPage = criteria(
                CatalogVisibility.PUBLIC,
                "clean code",
                PageRequest.of(1, 20, Sort.by("title").ascending())
        );
        var descending = criteria(
                CatalogVisibility.PUBLIC,
                "clean code",
                PageRequest.of(0, 20, Sort.by("title").descending())
        );

        assertThat(firstPage.cacheKey())
                .isNotEqualTo(secondPage.cacheKey())
                .isNotEqualTo(descending.cacheKey());
    }

    @Test
    @DisplayName("caches only the first ten pages with at most one hundred items")
    void cachesOnlyBoundedPages() {
        assertThat(criteria(CatalogVisibility.PUBLIC, "", PageRequest.of(9, 100)).cacheable()).isTrue();
        assertThat(criteria(CatalogVisibility.PUBLIC, "", PageRequest.of(10, 100)).cacheable()).isFalse();
        assertThat(criteria(CatalogVisibility.PUBLIC, "", PageRequest.of(0, 101)).cacheable()).isFalse();
        assertThat(criteria(CatalogVisibility.PUBLIC, "", Pageable.unpaged()).cacheable()).isFalse();
    }

    private MaterialSearchCriteria criteria(
            CatalogVisibility visibility,
            String query,
            Pageable pageable
    ) {
        return new MaterialSearchCriteria(
                visibility,
                query,
                MaterialType.BOOK,
                MaterialStatus.ACTIVE,
                2008,
                1L,
                2L,
                3L,
                pageable
        );
    }
}
