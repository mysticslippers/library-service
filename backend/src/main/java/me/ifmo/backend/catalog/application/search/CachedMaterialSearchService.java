package me.ifmo.backend.catalog.application.search;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.catalog.domain.enums.CopyStatus;
import me.ifmo.backend.catalog.persistence.MaterialRepository;
import me.ifmo.backend.catalog.web.response.MaterialResponse;
import me.ifmo.backend.shared.cache.CacheNames;
import me.ifmo.backend.shared.web.response.PageResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CachedMaterialSearchService {

    private final MaterialRepository materialRepository;
    private final MaterialResponseAssembler responseAssembler;

    @Cacheable(
            cacheNames = CacheNames.CATALOG_SEARCH,
            key = "#criteria.cacheKey()",
            condition = "#criteria.cacheable()",
            sync = true
    )
    @Transactional(readOnly = true)
    public PageResponse<MaterialResponse> search(MaterialSearchCriteria criteria) {

        var materials = materialRepository.search(criteria.query(), criteria.materialType(), criteria.status(),
                criteria.publicationYear(), criteria.authorId(), criteria.genreId(), criteria.branchId(),
                CopyStatus.REMOVED, criteria.pageable());

        Page<MaterialResponse> responses = materials.map(material ->
                responseAssembler.toResponse(material, criteria.includeRemovedCopies()));

        return PageResponse.from(responses);
    }
}
