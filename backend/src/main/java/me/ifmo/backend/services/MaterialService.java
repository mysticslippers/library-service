package me.ifmo.backend.services;

import me.ifmo.backend.dto.catalog.request.ChangeMaterialStatusRequest;
import me.ifmo.backend.dto.catalog.request.CreateMaterialRequest;
import me.ifmo.backend.dto.catalog.request.MaterialSearchRequest;
import me.ifmo.backend.dto.catalog.request.UpdateMaterialRequest;
import me.ifmo.backend.dto.catalog.response.MaterialResponse;
import me.ifmo.backend.shared.web.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface MaterialService {

    MaterialResponse create(CreateMaterialRequest request);

    MaterialResponse getMaterialById(Long actorUserId, Long id);

    MaterialResponse getMaterialByIsbn(Long actorUserId, String isbn);

    MaterialResponse update(Long id, UpdateMaterialRequest request);

    MaterialResponse changeStatus(Long id, ChangeMaterialStatusRequest request);

    PageResponse<MaterialResponse> search(Long actorUserId, MaterialSearchRequest request, Pageable pageable);
}
