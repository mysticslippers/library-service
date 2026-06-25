package me.ifmo.backend.services;

import me.ifmo.backend.dto.catalog.request.CreateMaterialCopyRequest;
import me.ifmo.backend.dto.catalog.response.MaterialCopyResponse;

public interface MaterialCopyService {

    MaterialCopyResponse create(CreateMaterialCopyRequest request);
}
