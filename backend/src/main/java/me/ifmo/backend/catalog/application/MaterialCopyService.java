package me.ifmo.backend.catalog.application;

import me.ifmo.backend.catalog.web.request.ChangeMaterialCopyStatusRequest;
import me.ifmo.backend.catalog.web.request.CreateMaterialCopyRequest;
import me.ifmo.backend.catalog.web.request.UpdateMaterialCopyRequest;
import me.ifmo.backend.catalog.web.response.MaterialCopyResponse;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.catalog.domain.enums.CopyStatus;
import org.springframework.data.domain.Pageable;

public interface MaterialCopyService {

    MaterialCopyResponse create(CreateMaterialCopyRequest request);

    MaterialCopyResponse getMaterialCopyById(Long id);

    MaterialCopyResponse getByInventoryNumber(String inventoryNumber);

    MaterialCopyResponse update(Long id, UpdateMaterialCopyRequest request);

    MaterialCopyResponse changeStatus(Long id, ChangeMaterialCopyStatusRequest request);

    PageResponse<MaterialCopyResponse> search(Long materialId, Long branchId, CopyStatus status, Pageable pageable);
}
