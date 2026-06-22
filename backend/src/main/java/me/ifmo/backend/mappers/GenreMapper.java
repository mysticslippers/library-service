package me.ifmo.backend.mappers;

import me.ifmo.backend.dto.catalog.request.CreateGenreRequest;
import me.ifmo.backend.entities.Genre;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueMappingStrategy;

@Mapper(nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface GenreMapper {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "code", source = "code")
    @Mapping(target = "name", source = "name")
    Genre toEntity(CreateGenreRequest request);
}
