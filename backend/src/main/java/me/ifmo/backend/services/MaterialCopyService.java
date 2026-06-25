package me.ifmo.backend.services;

import me.ifmo.backend.dto.catalog.request.ChangeMaterialCopyStatusRequest;
import me.ifmo.backend.dto.catalog.request.CreateMaterialCopyRequest;
import me.ifmo.backend.dto.catalog.request.UpdateMaterialCopyRequest;
import me.ifmo.backend.dto.catalog.response.MaterialCopyResponse;
import me.ifmo.backend.entities.enums.CopyStatus;
import org.springframework.data.domain.Pageable;

public interface MaterialCopyService {

    MaterialCopyResponse create(CreateMaterialCopyRequest request);

    MaterialCopyResponse getMaterialCopyById(Long id);

    MaterialCopyResponse getByInventoryNumber(String inventoryNumber);

    MaterialCopyResponse update(Long id, UpdateMaterialCopyRequest request);

    MaterialCopyResponse changeStatus(Long id, ChangeMaterialCopyStatusRequest request);

    PageResponse<MaterialCopyResponse> search(Long materialId, Long branchId, CopyStatus status, Pageable pageable);
}
