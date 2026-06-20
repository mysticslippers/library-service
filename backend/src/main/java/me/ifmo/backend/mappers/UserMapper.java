package me.ifmo.backend.mappers;

import me.ifmo.backend.dto.auth.request.RegisterRequest;
import me.ifmo.backend.dto.user.request.CreateUserRequest;
import me.ifmo.backend.dto.user.request.UpdateUserRequest;
import me.ifmo.backend.dto.user.response.UserProfileResponse;
import me.ifmo.backend.dto.user.response.UserShortResponse;
import me.ifmo.backend.entities.User;
import me.ifmo.backend.entities.enums.RoleCode;
import org.mapstruct.*;

import java.util.Collection;
import java.util.List;

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

        @BeanMapping(nullValuePropertyMappingStrategy =
                NullValuePropertyMappingStrategy.IGNORE)
        @Mapping(target = "id", ignore = true)
        @Mapping(target = "passwordHash", ignore = true)
        @Mapping(target = "status", ignore = true)
        @Mapping(target = "registeredAt", ignore = true)
        @Mapping(target = "activatedAt", ignore = true)
        @Mapping(target = "lastLoginAt", ignore = true)
        @Mapping(target = "failedLoginAttempts", ignore = true)
        @Mapping(target = "lockedUntil", ignore = true)
        @Mapping(target = "branch", ignore = true)
        void updateEntity(UpdateUserRequest request, @MappingTarget User user);

        UserShortResponse toShortResponse(User user);

        List<UserShortResponse> toShortResponseList(Collection<User> users);

        @Mapping(target = "homeBranchId", source = "user.branch.id")
        @Mapping(target = "homeBranchName", source = "user.branch.name")
        @Mapping(target = "roles", source = "roles")
        UserProfileResponse toProfileResponse(User user, Collection<RoleCode> roles);
}
