package me.ifmo.backend.notification.mapper;

import me.ifmo.backend.notification.web.response.NotificationPreferenceResponse;
import me.ifmo.backend.notification.domain.NotificationPreference;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface NotificationPreferenceMapper {

    @Mapping(target = "userId", source = "preference.user.id")
    @Mapping(target = "mandatory", source = "mandatory")
    NotificationPreferenceResponse toResponse(NotificationPreference preference, boolean mandatory);
}
