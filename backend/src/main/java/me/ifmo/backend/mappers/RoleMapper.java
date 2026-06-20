package me.ifmo.backend.mappers;

import me.ifmo.backend.dto.user.response.RoleResponse;
import me.ifmo.backend.entities.Role;
import me.ifmo.backend.entities.UserRole;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueMappingStrategy;

import java.util.Collection;
import java.util.List;

@Mapper(nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface RoleMapper {

    RoleResponse toResponse(Role role);

    List<RoleResponse> toResponseList(List<Role> roles);

    List<RoleResponse> toResponsesFromUserRoles(Collection<UserRole> userRoles);

    @Mapping(target = "id", source = "role.id")
    @Mapping(target = "code", source = "role.code")
    @Mapping(target = "name", source = "role.name")
    @Mapping(target = "description", source = "role.description")
    RoleResponse toResponse(UserRole userRole);
}
