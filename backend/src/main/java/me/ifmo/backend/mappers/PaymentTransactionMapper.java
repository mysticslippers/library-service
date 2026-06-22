package me.ifmo.backend.mappers;

import me.ifmo.backend.entities.Fine;
import me.ifmo.backend.entities.PaymentTransaction;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueMappingStrategy;

import java.math.BigDecimal;

@Mapper(nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface PaymentTransactionMapper {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "fine", source = "fine")
    @Mapping(target = "externalPayment", source = "externalPayment")
    @Mapping(target = "amount", source = "amount")
    PaymentTransaction toEntity(Fine fine, String externalPayment, BigDecimal amount);
}
