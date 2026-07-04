package me.ifmo.backend.mappers;

import me.ifmo.backend.dto.notification.response.NotificationPreferenceResponse;
import me.ifmo.backend.entities.NotificationPreference;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface NotificationPreferenceMapper {

    @Mapping(target = "userId", source = "preference.user.id")
    @Mapping(target = "mandatory", source = "mandatory")
    NotificationPreferenceResponse toResponse(NotificationPreference preference, boolean mandatory);
}
