package me.ifmo.backend.mappers;

import me.ifmo.backend.dto.fine.request.CreateFineTariffRequest;
import me.ifmo.backend.dto.fine.request.UpdateFineTariffRequest;
import me.ifmo.backend.dto.fine.response.FineTariffResponse;
import me.ifmo.backend.entities.FineTariff;
import org.mapstruct.*;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper(nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface FineTariffMapper {

    @BeanMapping(nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "validFrom", ignore = true)
    FineTariff toEntity(CreateFineTariffRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "violationType", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "validFrom", ignore = true)
    void updateEntity(UpdateFineTariffRequest request, @MappingTarget FineTariff tariff);

    FineTariffResponse toResponse(FineTariff tariff);

    List<FineTariffResponse> toResponseList(Collection<FineTariff> tariffs);
}
