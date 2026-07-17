package me.ifmo.backend.fine.mapper;

import me.ifmo.backend.fine.web.request.UpdatePaymentStatusRequest;
import me.ifmo.backend.fine.web.response.PaymentTransactionResponse;
import me.ifmo.backend.fine.domain.Fine;
import me.ifmo.backend.fine.domain.PaymentTransaction;
import org.mapstruct.*;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

@Mapper(nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface PaymentTransactionMapper {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "fine", source = "fine")
    @Mapping(target = "externalPayment", source = "externalPayment")
    @Mapping(target = "amount", source = "amount")
    PaymentTransaction toEntity(Fine fine, String externalPayment, BigDecimal amount);

    @BeanMapping(ignoreByDefault = true, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "status", source = "status")
    @Mapping(target = "externalPayment", source = "externalPayment")
    void updateStatus(UpdatePaymentStatusRequest request, @MappingTarget PaymentTransaction transaction);

    @Mapping(target = "fineId", source = "fine.id")
    PaymentTransactionResponse toResponse(PaymentTransaction transaction);

    List<PaymentTransactionResponse> toResponseList(Collection<PaymentTransaction> transactions);
}
