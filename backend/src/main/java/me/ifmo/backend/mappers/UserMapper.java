package me.ifmo.backend.mappers;

import me.ifmo.backend.dto.auth.request.RegisterRequest;
import me.ifmo.backend.dto.user.request.CreateUserRequest;
import me.ifmo.backend.entities.User;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueMappingStrategy;

@Mapper(uses = {RoleMapper.class},
        nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface UserMapper {

        @BeanMapping(ignoreByDefault = true)
        @Mapping(target = "email", source = "email")
        @Mapping(target = "phone", source = "phone")
        @Mapping(target = "firstName", source = "firstName")
        @Mapping(target = "lastName", source = "lastName")
        @Mapping(target = "middleName", source = "middleName")
        User toEntity(CreateUserRequest request);

        @BeanMapping(ignoreByDefault = true)
        @Mapping(target = "email", source = "email")
        @Mapping(target = "phone", source = "phone")
        @Mapping(target = "firstName", source = "firstName")
        @Mapping(target = "lastName", source = "lastName")
        @Mapping(target = "middleName", source = "middleName")
        User toEntity(RegisterRequest request);
}
