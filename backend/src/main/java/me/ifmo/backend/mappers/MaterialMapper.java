package me.ifmo.backend.mappers;

import me.ifmo.backend.dto.catalog.request.CreateMaterialRequest;
import me.ifmo.backend.dto.catalog.request.UpdateMaterialRequest;
import me.ifmo.backend.entities.Material;
import org.mapstruct.*;

@Mapper(uses = {MaterialAuthorMapper.class, MaterialGenreMapper.class})
public interface MaterialMapper {

    @BeanMapping(nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Material toEntity(CreateMaterialRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpdateMaterialRequest request, @MappingTarget Material material);
}
