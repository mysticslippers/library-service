package me.ifmo.backend.catalog.mapper;

import me.ifmo.backend.catalog.domain.Material;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MaterialCoverUrlFactory {

    @Named("materialCoverUrl")
    public String create(Material material) {
        if (material == null || material.getId() == null
                || material.getCoverObjectKey() == null || material.getCoverVersion() == null)
            return null;

        return create(material.getId(), material.getCoverVersion());
    }

    public String create(Long materialId, UUID version) {
        return "/api/materials/%d/cover?v=%s".formatted(materialId, version);
    }
}
