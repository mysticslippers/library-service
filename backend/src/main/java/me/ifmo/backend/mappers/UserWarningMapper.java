package me.ifmo.backend.mappers;

import org.mapstruct.*;

@Mapper(uses = UserMapper.class,
        nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface UserWarningMapper {
}
