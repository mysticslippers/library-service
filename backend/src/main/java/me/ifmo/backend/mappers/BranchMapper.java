package me.ifmo.backend.mappers;

import me.ifmo.backend.dto.library.request.CreateBranchRequest;
import me.ifmo.backend.dto.library.request.UpdateBranchRequest;
import me.ifmo.backend.entities.Branch;
import me.ifmo.backend.entities.Library;
import org.mapstruct.*;

@Mapper(uses = {LibraryMapper.class, BranchAddressJsonMapper.class},
        nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface BranchMapper {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "library", source = "library")
    @Mapping(target = "name", source = "request.name")
    @Mapping(target = "address", source = "request.address")
    Branch toEntity(CreateBranchRequest request, Library library);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "library", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateEntity(UpdateBranchRequest request, @MappingTarget Branch branch);
}
