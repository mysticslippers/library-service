package me.ifmo.backend.catalog.application.search;

import me.ifmo.backend.catalog.domain.Material;
import me.ifmo.backend.catalog.domain.enums.CopyStatus;
import me.ifmo.backend.catalog.domain.enums.MaterialStatus;
import me.ifmo.backend.catalog.domain.enums.MaterialType;
import me.ifmo.backend.catalog.persistence.MaterialRepository;
import me.ifmo.backend.catalog.web.response.MaterialResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Cached material search service")
class CachedMaterialSearchServiceTest {

    @Mock
    private MaterialRepository materialRepository;

    @Mock
    private MaterialResponseAssembler responseAssembler;

    @InjectMocks
    private CachedMaterialSearchService service;

    @Test
    @DisplayName("returns public search results without removed copies")
    void returnsPublicSearchResultsWithoutRemovedCopies() {
        var pageable = PageRequest.of(0, 20);
        var criteria = criteria(CatalogVisibility.PUBLIC, pageable);
        var material = mock(Material.class);
        var response = response(1L);
        when(materialRepository.search(
                "clean code",
                MaterialType.BOOK,
                MaterialStatus.ACTIVE,
                2008,
                1L,
                2L,
                3L,
                CopyStatus.REMOVED,
                pageable
        )).thenReturn(new PageImpl<>(List.of(material), pageable, 1));
        when(responseAssembler.toResponse(material, false)).thenReturn(response);

        var result = service.search(criteria);

        assertThat(result.content()).containsExactly(response);
        verify(responseAssembler).toResponse(material, false);
    }

    @Test
    @DisplayName("returns staff search results including removed copies")
    void returnsStaffSearchResultsIncludingRemovedCopies() {
        var pageable = PageRequest.of(0, 20);
        var criteria = criteria(CatalogVisibility.STAFF, pageable);
        var material = mock(Material.class);
        var response = response(1L);
        when(materialRepository.search(
                "clean code",
                MaterialType.BOOK,
                MaterialStatus.ACTIVE,
                2008,
                1L,
                2L,
                3L,
                CopyStatus.REMOVED,
                pageable
        )).thenReturn(new PageImpl<>(List.of(material), pageable, 1));
        when(responseAssembler.toResponse(material, true)).thenReturn(response);

        var result = service.search(criteria);

        assertThat(result.content()).containsExactly(response);
        verify(responseAssembler).toResponse(material, true);
    }

    private MaterialSearchCriteria criteria(CatalogVisibility visibility, PageRequest pageable) {
        return new MaterialSearchCriteria(
                visibility,
                "clean code",
                MaterialType.BOOK,
                MaterialStatus.ACTIVE,
                2008,
                1L,
                2L,
                3L,
                pageable
        );
    }

    private MaterialResponse response(Long id) {
        return new MaterialResponse(
                id,
                "9780132350884",
                "Clean Code",
                null,
                null,
                2008,
                MaterialType.BOOK,
                "EN",
                MaterialStatus.ACTIVE,
                null,
                List.of(),
                List.of(),
                1,
                1,
                List.of()
        );
    }
}
