package me.ifmo.backend.notification.mapper;

import me.ifmo.backend.notification.web.request.CreateNotificationTemplateRequest;
import me.ifmo.backend.notification.web.request.UpdateNotificationTemplateRequest;
import me.ifmo.backend.notification.web.response.NotificationTemplateResponse;
import me.ifmo.backend.notification.domain.NotificationTemplate;
import org.mapstruct.*;

@Mapper
public interface NotificationTemplateMapper {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "type", source = "type")
    @Mapping(target = "channel", source = "channel")
    @Mapping(target = "subjectTemplate", source = "subjectTemplate")
    @Mapping(target = "bodyTemplate", source = "bodyTemplate")
    @Mapping(target = "requiredParameters", source = "requiredParameters")
    NotificationTemplate toEntity(CreateNotificationTemplateRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "type", ignore = true)
    @Mapping(target = "channel", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpdateNotificationTemplateRequest request, @MappingTarget NotificationTemplate template);

    NotificationTemplateResponse toResponse(NotificationTemplate template);
}
