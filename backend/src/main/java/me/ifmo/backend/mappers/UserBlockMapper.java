package me.ifmo.backend.mappers;

import me.ifmo.backend.dto.user.request.CreateUserBlockRequest;
import me.ifmo.backend.entities.User;
import me.ifmo.backend.entities.UserBlock;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueMappingStrategy;

@Mapper(uses = UserMapper.class,
        nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface UserBlockMapper {


}
