package me.ifmo.backend.mappers;

import me.ifmo.backend.dto.library.request.CreateBranchRequest;
import me.ifmo.backend.entities.Branch;
import me.ifmo.backend.entities.Library;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueMappingStrategy;

@Mapper(uses = {LibraryMapper.class, BranchAddressJsonMapper.class},
        nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface BranchMapper {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "library", source = "library")
    @Mapping(target = "name", source = "request.name")
    @Mapping(target = "address", source = "request.address")
    Branch toEntity(CreateBranchRequest request, Library library);
}
