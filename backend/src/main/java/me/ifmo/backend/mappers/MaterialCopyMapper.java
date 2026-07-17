package me.ifmo.backend.mappers;

import me.ifmo.backend.library.internal.mapper.BranchMapper;

import me.ifmo.backend.dto.catalog.request.CreateMaterialCopyRequest;
import me.ifmo.backend.dto.catalog.request.UpdateMaterialCopyRequest;
import me.ifmo.backend.dto.catalog.response.MaterialCopyResponse;
import me.ifmo.backend.library.internal.domain.Branch;
import me.ifmo.backend.entities.Material;
import me.ifmo.backend.entities.MaterialCopy;
import org.mapstruct.*;

import java.util.Collection;
import java.util.List;

@Mapper( uses = BranchMapper.class,
        nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface MaterialCopyMapper {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "material", source = "material")
    @Mapping(target = "branch", source = "branch")
    @Mapping(target = "inventoryNumber", source = "request.inventoryNumber")
    @Mapping(target = "shelfLocation", source = "request.shelfLocation")
    MaterialCopy toEntity(CreateMaterialCopyRequest request, Branch branch, Material material);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "material", ignore = true)
    @Mapping(target = "branch", ignore = true)
    @Mapping(target = "inventoryNumber", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpdateMaterialCopyRequest request, @MappingTarget MaterialCopy entity);

    @Mapping(target = "materialId", source = "material.id")
    @Mapping(target = "materialTitle", source = "material.title")
    MaterialCopyResponse toResponse(MaterialCopy copy);

    List<MaterialCopyResponse> toResponseList(Collection<MaterialCopy> copies);
}
