package me.ifmo.backend.mappers;

import me.ifmo.backend.entities.Genre;
import me.ifmo.backend.entities.Material;
import me.ifmo.backend.entities.MaterialGenre;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueMappingStrategy;

@Mapper(uses = GenreMapper.class,
        nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface MaterialGenreMapper {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", expression = "java(new MaterialGenreId(material.getId(), genre.getId()))")
    @Mapping(target = "material", source = "material")
    @Mapping(target = "genre", source = "genre")
    MaterialGenre toEntity(Material material, Genre genre);
}
