package me.ifmo.backend.mappers;

import me.ifmo.backend.dto.audit.response.AuditLogResponse;
import me.ifmo.backend.entities.AuditLog;
import me.ifmo.backend.user.domain.User;
import me.ifmo.backend.user.mapper.UserMapper;
import me.ifmo.backend.entities.enums.AuditAction;
import me.ifmo.backend.entities.enums.AuditEntityType;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueMappingStrategy;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mapper(uses = {UserMapper.class, AuditDetailsJsonMapper.class},
        nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface AuditLogMapper {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "user", source = "actor")
    @Mapping(target = "type", source = "entityType")
    @Mapping(target = "entityId", source = "entityId")
    @Mapping(target = "action", source = "action")
    @Mapping(target = "details", source = "details")
    AuditLog toEntity(User actor, AuditEntityType entityType, Long entityId, AuditAction action, Map<String, Object> details);

    @Mapping(target = "actor", source = "user")
    AuditLogResponse toResponse(AuditLog auditLog);

    List<AuditLogResponse> toResponseList(Collection<AuditLog> auditLogs);
}
