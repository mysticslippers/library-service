package me.ifmo.backend.catalog.application.impl;

import me.ifmo.backend.catalog.application.search.CachedMaterialSearchService;
import me.ifmo.backend.catalog.application.search.CatalogVisibility;
import me.ifmo.backend.catalog.application.search.MaterialResponseAssembler;
import me.ifmo.backend.catalog.application.search.MaterialSearchCriteria;
import me.ifmo.backend.catalog.domain.enums.MaterialStatus;
import me.ifmo.backend.catalog.domain.enums.MaterialType;
import me.ifmo.backend.catalog.mapper.MaterialMapper;
import me.ifmo.backend.catalog.persistence.AuthorRepository;
import me.ifmo.backend.catalog.persistence.GenreRepository;
import me.ifmo.backend.catalog.persistence.MaterialAuthorRepository;
import me.ifmo.backend.catalog.persistence.MaterialCopyRepository;
import me.ifmo.backend.catalog.persistence.MaterialGenreRepository;
import me.ifmo.backend.catalog.persistence.MaterialRepository;
import me.ifmo.backend.catalog.web.request.MaterialSearchRequest;
import me.ifmo.backend.catalog.web.response.MaterialResponse;
import me.ifmo.backend.circulation.persistence.LoanRepository;
import me.ifmo.backend.circulation.persistence.ReservationRepository;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.user.domain.enums.RoleCode;
import me.ifmo.backend.user.persistence.UserRoleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Material service catalog cache delegation")
class MaterialServiceImplCacheTest {

    private static final long USER_ID = 42L;

    @Mock
    private MaterialRepository repository;
    @Mock
    private AuthorRepository authorRepository;
    @Mock
    private GenreRepository genreRepository;
    @Mock
    private MaterialAuthorRepository materialAuthorRepository;
    @Mock
    private MaterialCopyRepository materialCopyRepository;
    @Mock
    private MaterialGenreRepository materialGenreRepository;
    @Mock
    private LoanRepository loanRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private MaterialMapper materialMapper;
    @Mock
    private MaterialResponseAssembler responseAssembler;
    @Mock
    private CachedMaterialSearchService cachedMaterialSearchService;

    @InjectMocks
    private MaterialServiceImpl service;

    @Test
    @DisplayName("uses public visibility and active status for readers")
    void usesPublicVisibilityAndActiveStatusForReaders() {
        var pageable = PageRequest.of(0, 20);
        var request = new MaterialSearchRequest(
                "  clean code  ",
                MaterialType.BOOK,
                null,
                2008,
                1L,
                2L,
                3L
        );
        var expected = emptyPage();
        when(userRoleRepository.findRoleCodesByUser_Id(USER_ID)).thenReturn(List.of(RoleCode.READER));
        when(cachedMaterialSearchService.search(org.mockito.ArgumentMatchers.any()))
                .thenReturn(expected);

        var result = service.search(USER_ID, request, pageable);

        var criteria = capturedCriteria();
        assertThat(result).isSameAs(expected);
        assertThat(criteria.visibility()).isEqualTo(CatalogVisibility.PUBLIC);
        assertThat(criteria.status()).isEqualTo(MaterialStatus.ACTIVE);
        assertThat(criteria.query()).isEqualTo("clean code");
        assertThat(criteria.pageable()).isSameAs(pageable);
    }

    @Test
    @DisplayName("uses staff visibility and requested status for librarians")
    void usesStaffVisibilityAndRequestedStatusForLibrarians() {
        var pageable = PageRequest.of(0, 20);
        var request = new MaterialSearchRequest(
                null,
                null,
                MaterialStatus.HIDDEN,
                null,
                null,
                null,
                null
        );
        when(userRoleRepository.findRoleCodesByUser_Id(USER_ID)).thenReturn(List.of(RoleCode.LIBRARIAN));
        when(cachedMaterialSearchService.search(org.mockito.ArgumentMatchers.any()))
                .thenReturn(emptyPage());

        service.search(USER_ID, request, pageable);

        var criteria = capturedCriteria();
        assertThat(criteria.visibility()).isEqualTo(CatalogVisibility.STAFF);
        assertThat(criteria.status()).isEqualTo(MaterialStatus.HIDDEN);
        assertThat(criteria.query()).isEmpty();
    }

    @Test
    @DisplayName("rejects non-active status searches from readers before cache lookup")
    void rejectsNonActiveStatusSearchesFromReadersBeforeCacheLookup() {
        var request = new MaterialSearchRequest(
                null,
                null,
                MaterialStatus.HIDDEN,
                null,
                null,
                null,
                null
        );
        when(userRoleRepository.findRoleCodesByUser_Id(USER_ID)).thenReturn(List.of(RoleCode.READER));

        assertThatThrownBy(() -> service.search(USER_ID, request, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);
        verify(cachedMaterialSearchService, never())
                .search(org.mockito.ArgumentMatchers.any());
    }

    private MaterialSearchCriteria capturedCriteria() {
        var captor = ArgumentCaptor.forClass(MaterialSearchCriteria.class);
        verify(cachedMaterialSearchService).search(captor.capture());
        return captor.getValue();
    }

    private PageResponse<MaterialResponse> emptyPage() {
        return new PageResponse<>(List.of(), 0, 20, 0, 0, true, true, true);
    }
}
