package me.ifmo.backend.library.internal.mapper;

import me.ifmo.backend.library.internal.web.request.CreateLibraryRequest;
import me.ifmo.backend.library.internal.web.request.UpdateLibraryRequest;
import me.ifmo.backend.library.internal.web.response.LibraryResponse;
import me.ifmo.backend.library.internal.web.response.LibraryShortResponse;
import me.ifmo.backend.library.internal.domain.Library;
import org.mapstruct.*;

import java.util.Collection;
import java.util.List;

@Mapper(nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface LibraryMapper {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "code", source = "code")
    @Mapping(target = "name", source = "name")
    Library toEntity(CreateLibraryRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateEntity(UpdateLibraryRequest request, @MappingTarget Library library);

    LibraryResponse toResponse(Library library);

    LibraryShortResponse toShortResponse(Library library);

    List<LibraryResponse> toResponseList(Collection<Library> libraries);

    List<LibraryShortResponse> toShortResponseList(Collection<Library> libraries);
}
