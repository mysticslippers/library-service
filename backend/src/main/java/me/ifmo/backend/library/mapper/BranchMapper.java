package me.ifmo.backend.library.mapper;

import me.ifmo.backend.library.web.request.CreateBranchRequest;
import me.ifmo.backend.library.web.request.UpdateBranchRequest;
import me.ifmo.backend.library.web.response.BranchResponse;
import me.ifmo.backend.library.web.response.BranchShortResponse;
import me.ifmo.backend.library.domain.Branch;
import me.ifmo.backend.library.domain.Library;
import org.mapstruct.*;

import java.util.Collection;
import java.util.List;

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

    BranchResponse toResponse(Branch branch);

    @Mapping(target = "libraryId", source = "library.id")
    BranchShortResponse toShortResponse(Branch branch);

    List<BranchResponse> toResponseList(Collection<Branch> branches);

    List<BranchShortResponse> toShortResponseList(Collection<Branch> branches);
}
