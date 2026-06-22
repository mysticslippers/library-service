package me.ifmo.backend.mappers;

import me.ifmo.backend.dto.notification.request.CreateNotificationRequest;
import me.ifmo.backend.dto.notification.request.UpdateNotificationStatusRequest;
import me.ifmo.backend.entities.*;
import org.mapstruct.*;

import java.time.LocalDateTime;

@Mapper(uses = UserMapper.class, nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface NotificationMapper {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "user", source = "user")
    @Mapping(target = "reservation", source = "reservation")
    @Mapping(target = "loan", source = "loan")
    @Mapping(target = "fine", source = "fine")
    @Mapping(target = "type", source = "request.type")
    @Mapping(target = "channel", source = "request.channel")
    @Mapping(target = "subject", source = "request.subject")
    @Mapping(target = "body", source = "request.body")
    Notification toEntity(CreateNotificationRequest request, User user, Reservation reservation, Loan loan, Fine fine);

    @BeanMapping(ignoreByDefault = true, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "status", source = "request.status")
    @Mapping(target = "sentAt", source = "sentAt")
    void updateStatus(UpdateNotificationStatusRequest request, LocalDateTime sentAt, @MappingTarget Notification notification);
}
