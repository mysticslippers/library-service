package me.ifmo.backend.mappers;

import me.ifmo.backend.dto.user.request.CreateUserWarningRequest;
import me.ifmo.backend.entities.User;
import me.ifmo.backend.entities.UserWarning;
import org.mapstruct.*;

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
}
