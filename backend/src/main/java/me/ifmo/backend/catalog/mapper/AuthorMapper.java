package me.ifmo.backend.catalog.mapper;

import me.ifmo.backend.catalog.web.request.CreateAuthorRequest;
import me.ifmo.backend.catalog.web.request.UpdateAuthorRequest;
import me.ifmo.backend.catalog.web.response.AuthorResponse;
import me.ifmo.backend.catalog.domain.Author;
import org.mapstruct.*;

import java.util.Collection;
import java.util.List;

@Mapper(nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface AuthorMapper {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "firstName", source = "firstName")
    @Mapping(target = "middleName", source = "middleName")
    @Mapping(target = "lastName", source = "lastName")
    @Mapping(target = "status", ignore = true)
    Author toEntity(CreateAuthorRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "status", ignore = true)
    void updateEntity(UpdateAuthorRequest request, @MappingTarget Author author);

    AuthorResponse toResponse(Author author);

    List<AuthorResponse> toResponseList(Collection<Author> authors);
}
