package me.ifmo.backend.mappers;

import me.ifmo.backend.dto.catalog.response.MaterialAuthorResponse;
import me.ifmo.backend.entities.Author;
import me.ifmo.backend.entities.Material;
import me.ifmo.backend.entities.MaterialAuthor;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueMappingStrategy;

@Mapper(uses = AuthorMapper.class,
        nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface MaterialAuthorMapper {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", expression = "java(new MaterialAuthorId(material.getId(), author.getId()))")
    @Mapping(target = "material", source = "material")
    @Mapping(target = "author", source = "author")
    @Mapping(target = "authorOrder", source = "authorOrder")
    MaterialAuthor toEntity(Material material, Author author, Integer authorOrder);

    MaterialAuthorResponse toResponse(MaterialAuthor materialAuthor);
}
