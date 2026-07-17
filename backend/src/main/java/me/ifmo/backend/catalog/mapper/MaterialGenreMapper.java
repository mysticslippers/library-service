package me.ifmo.backend.catalog.mapper;

import me.ifmo.backend.catalog.web.response.GenreResponse;
import me.ifmo.backend.catalog.domain.Genre;
import me.ifmo.backend.catalog.domain.Material;
import me.ifmo.backend.catalog.domain.MaterialGenre;
import me.ifmo.backend.catalog.domain.id.MaterialGenreId;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueMappingStrategy;

import java.util.Collection;
import java.util.List;

@Mapper(uses = GenreMapper.class,
        imports = MaterialGenreId.class,
        nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface MaterialGenreMapper {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", expression = "java(new MaterialGenreId(material.getId(), genre.getId()))")
    @Mapping(target = "material", source = "material")
    @Mapping(target = "genre", source = "genre")
    MaterialGenre toEntity(Material material, Genre genre);

    @Mapping(target = "id", source = "genre.id")
    @Mapping(target = "code", source = "genre.code")
    @Mapping(target = "name", source = "genre.name")
    @Mapping(target = "status", source = "genre.status")
    GenreResponse toResponse(MaterialGenre materialGenre);

    List<GenreResponse> toResponseList(Collection<MaterialGenre> materialGenres);
}
