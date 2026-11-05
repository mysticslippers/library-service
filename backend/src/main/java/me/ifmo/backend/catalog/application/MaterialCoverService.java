package me.ifmo.backend.catalog.application;

import me.ifmo.backend.catalog.application.cover.MaterialCoverContent;
import me.ifmo.backend.catalog.web.response.MaterialCoverResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface MaterialCoverService {

    MaterialCoverResponse upload(Long materialId, MultipartFile file);

    MaterialCoverContent getPublic(Long materialId, UUID requestedVersion);

    void delete(Long materialId);
}
