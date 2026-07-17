package me.ifmo.backend.catalog.mapper;

import me.ifmo.backend.catalog.web.request.CreateGenreRequest;
import me.ifmo.backend.catalog.web.request.UpdateGenreRequest;
import me.ifmo.backend.catalog.web.response.GenreResponse;
import me.ifmo.backend.catalog.domain.Genre;
import org.mapstruct.*;

import java.util.Collection;
import java.util.List;

@Mapper(nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface GenreMapper {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "code", source = "code")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "status", ignore = true)
    Genre toEntity(CreateGenreRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "status", ignore = true)
    void updateEntity(UpdateGenreRequest request, @MappingTarget Genre genre);

    GenreResponse toResponse(Genre genre);

    List<GenreResponse> toResponseList(Collection<Genre> genres);
}
