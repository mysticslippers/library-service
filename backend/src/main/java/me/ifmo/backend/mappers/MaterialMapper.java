package me.ifmo.backend.mappers;

import me.ifmo.backend.dto.catalog.request.CreateMaterialRequest;
import me.ifmo.backend.entities.Material;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueCheckStrategy;

@Mapper(uses = {MaterialAuthorMapper.class, MaterialGenreMapper.class})
public interface MaterialMapper {

    @BeanMapping(nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Material toEntity(CreateMaterialRequest request);
}
