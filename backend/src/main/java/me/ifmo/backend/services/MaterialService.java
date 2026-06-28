package me.ifmo.backend.services;

import me.ifmo.backend.dto.catalog.request.ChangeMaterialStatusRequest;
import me.ifmo.backend.dto.catalog.request.CreateMaterialRequest;
import me.ifmo.backend.dto.catalog.request.MaterialSearchRequest;
import me.ifmo.backend.dto.catalog.request.UpdateMaterialRequest;
import me.ifmo.backend.dto.catalog.response.MaterialResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MaterialService {

    MaterialResponse create(CreateMaterialRequest request);

    MaterialResponse getMaterialById(Long id);

    MaterialResponse getMaterialByIsbn(String isbn);

    MaterialResponse update(Long id, UpdateMaterialRequest request);

    MaterialResponse changeStatus(Long id, ChangeMaterialStatusRequest request);

    Page<MaterialResponse> search(MaterialSearchRequest request, Pageable pageable);
}
