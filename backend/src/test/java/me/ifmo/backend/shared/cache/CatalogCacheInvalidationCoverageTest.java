package me.ifmo.backend.shared.cache;

import me.ifmo.backend.catalog.application.impl.AuthorServiceImpl;
import me.ifmo.backend.catalog.application.impl.GenreServiceImpl;
import me.ifmo.backend.catalog.application.impl.MaterialCopyServiceImpl;
import me.ifmo.backend.catalog.application.impl.MaterialServiceImpl;
import me.ifmo.backend.circulation.application.impl.LoanServiceImpl;
import me.ifmo.backend.circulation.application.impl.ReservationServiceImpl;
import me.ifmo.backend.library.application.impl.BranchServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Catalog cache invalidation coverage")
class CatalogCacheInvalidationCoverageTest {

    @Test
    @DisplayName("invalidates search after every operation that changes cached catalog data")
    void invalidatesSearchAfterRelevantMutations() {
        assertInvalidated(MaterialServiceImpl.class, "create", "update", "changeStatus");
        assertInvalidated(MaterialCopyServiceImpl.class, "create", "update", "changeStatus");
        assertInvalidated(AuthorServiceImpl.class, "create", "update", "delete");
        assertInvalidated(GenreServiceImpl.class, "create", "update", "delete");
        assertInvalidated(BranchServiceImpl.class, "create", "update", "changeStatus");
        assertInvalidated(LoanServiceImpl.class, "create", "returnLoan", "markLost");
        assertInvalidated(
                ReservationServiceImpl.class,
                "create",
                "cancelByUser",
                "cancelByLibrarian",
                "expire"
        );
    }

    private void assertInvalidated(Class<?> type, String... methodNames) {
        var expectedNames = Set.of(methodNames);
        var methods = Arrays.stream(type.getDeclaredMethods())
                .filter(method -> expectedNames.contains(method.getName()))
                .toList();

        assertThat(methods)
                .as("%s cache-sensitive mutation methods", type.getSimpleName())
                .hasSize(expectedNames.size())
                .allSatisfy(method -> assertThat(method.getAnnotation(InvalidateCatalogSearch.class))
                        .as("%s.%s must invalidate catalog search", type.getSimpleName(), method.getName())
                        .isNotNull());
    }
}
