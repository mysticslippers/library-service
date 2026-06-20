package me.ifmo.backend.mappers;

import me.ifmo.backend.dto.user.request.CreateUserBlockRequest;
import me.ifmo.backend.dto.user.response.UserBlockResponse;
import me.ifmo.backend.entities.User;
import me.ifmo.backend.entities.UserBlock;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueMappingStrategy;

@Mapper(uses = UserMapper.class,
        nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface UserBlockMapper {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "user", source = "user")
    @Mapping(target = "createdByUser", source = "createdByUser")
    @Mapping(target = "reason", source = "request.reason")
    @Mapping(target = "expiresAt", source = "request.expiresAt")
    UserBlock toEntity(CreateUserBlockRequest request, User user, User createdByUser);

    UserBlockResponse toResponse(UserBlock userBlock);
}
