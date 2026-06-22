package me.ifmo.backend.mappers;

import me.ifmo.backend.dto.catalog.request.CreateMaterialCopyRequest;
import me.ifmo.backend.entities.MaterialCopy;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueMappingStrategy;

@Mapper(nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface MaterialCopyMapper {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "inventoryNumber", source = "inventoryNumber")
    @Mapping(target = "shelfLocation", source = "shelfLocation")
    MaterialCopy toEntity(CreateMaterialCopyRequest request);
}
