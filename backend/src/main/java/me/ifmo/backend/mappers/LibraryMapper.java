package me.ifmo.backend.mappers;

import me.ifmo.backend.dto.library.request.CreateLibraryRequest;
import me.ifmo.backend.entities.Library;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueMappingStrategy;

@Mapper(nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface LibraryMapper {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "code", source = "code")
    @Mapping(target = "name", source = "name")
    Library toEntity(CreateLibraryRequest request);
}
