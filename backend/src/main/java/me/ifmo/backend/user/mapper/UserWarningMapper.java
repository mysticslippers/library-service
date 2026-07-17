package me.ifmo.backend.user.mapper;

import me.ifmo.backend.user.web.request.CreateUserWarningRequest;
import me.ifmo.backend.user.web.response.UserWarningResponse;
import me.ifmo.backend.user.domain.User;
import me.ifmo.backend.user.domain.UserWarning;
import org.mapstruct.*;

import java.util.Collection;
import java.util.List;

@Mapper(uses = UserMapper.class,
        nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface UserWarningMapper {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "user", source = "user")
    @Mapping(target = "createdByUser", source = "createdByUser")
    @Mapping(target = "reason", source = "request.reason")
    @Mapping(target = "comment", source = "request.comment")
    @Mapping(target = "expiresAt", source = "request.expiresAt")
    UserWarning toEntity(CreateUserWarningRequest request, User user, User createdByUser);

    UserWarningResponse toResponse(UserWarning userWarning);

    List<UserWarningResponse> toResponseList(Collection<UserWarning> userWarnings);
}
