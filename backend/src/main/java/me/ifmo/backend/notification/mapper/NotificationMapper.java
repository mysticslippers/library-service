package me.ifmo.backend.notification.mapper;

import me.ifmo.backend.fine.domain.Fine;

import me.ifmo.backend.circulation.domain.Loan;
import me.ifmo.backend.circulation.domain.Reservation;

import me.ifmo.backend.user.domain.User;
import me.ifmo.backend.user.mapper.UserMapper;

import me.ifmo.backend.notification.domain.Notification;
import me.ifmo.backend.notification.web.request.CreateNotificationRequest;
import me.ifmo.backend.notification.web.request.UpdateNotificationStatusRequest;
import me.ifmo.backend.notification.web.response.NotificationResponse;
import org.mapstruct.*;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

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
    @Mapping(target = "externalMessageId", source = "request.externalMessageId")
    @Mapping(target = "errorMessage", source = "request.errorMessage")
    void updateStatus(UpdateNotificationStatusRequest request, LocalDateTime sentAt, @MappingTarget Notification notification);

    @Mapping(target = "reservationId", source = "reservation.id")
    @Mapping(target = "loanId", source = "loan.id")
    @Mapping(target = "fineId", source = "fine.id")
    NotificationResponse toResponse(Notification notification);

    List<NotificationResponse> toResponseList(Collection<Notification> notifications);
}
