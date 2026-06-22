package me.ifmo.backend.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.NullValueMappingStrategy;

@Mapper(uses = {UserMapper.class, MaterialCopyMapper.class},
        nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface FineMapper {

}
