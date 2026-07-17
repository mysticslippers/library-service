package me.ifmo.backend.user.mapper;

import me.ifmo.backend.user.web.request.CreateUserBlockRequest;
import me.ifmo.backend.user.web.response.UserBlockResponse;
import me.ifmo.backend.user.domain.User;
import me.ifmo.backend.user.domain.UserBlock;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueMappingStrategy;

import java.util.Collection;
import java.util.List;

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

    List<UserBlockResponse> toResponseList(Collection<UserBlock> userBlocks);
}
