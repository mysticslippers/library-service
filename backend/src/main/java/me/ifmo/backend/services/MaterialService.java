package me.ifmo.backend.services;

import me.ifmo.backend.dto.catalog.request.CreateMaterialRequest;
import me.ifmo.backend.dto.catalog.response.MaterialResponse;

public interface MaterialService {

    MaterialResponse create(CreateMaterialRequest request);
}
