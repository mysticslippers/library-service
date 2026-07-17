package me.ifmo.backend.catalog.mapper;

import me.ifmo.backend.catalog.web.response.AuthorResponse;
import me.ifmo.backend.catalog.web.response.MaterialAuthorResponse;
import me.ifmo.backend.catalog.domain.Author;
import me.ifmo.backend.catalog.domain.Material;
import me.ifmo.backend.catalog.domain.MaterialAuthor;
import me.ifmo.backend.catalog.domain.id.MaterialAuthorId;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueMappingStrategy;

import java.util.Collection;
import java.util.List;

@Mapper(uses = AuthorMapper.class,
        imports = MaterialAuthorId.class,
        nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface MaterialAuthorMapper {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", expression = "java(new MaterialAuthorId(material.getId(), author.getId()))")
    @Mapping(target = "material", source = "material")
    @Mapping(target = "author", source = "author")
    @Mapping(target = "authorOrder", source = "authorOrder")
    MaterialAuthor toEntity(Material material, Author author, Integer authorOrder);

    MaterialAuthorResponse toResponse(MaterialAuthor materialAuthor);

    List<MaterialAuthorResponse> toResponseList(Collection<MaterialAuthor> materialAuthors);

    @Mapping(target = ".", source = "author")
    AuthorResponse toAuthorResponse(MaterialAuthor materialAuthor);

    List<AuthorResponse> toAuthorResponseList(Collection<MaterialAuthor> materialAuthors);
}
