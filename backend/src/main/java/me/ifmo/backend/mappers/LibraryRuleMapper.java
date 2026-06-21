package me.ifmo.backend.mappers;

import me.ifmo.backend.dto.library.request.CreateLibraryRuleRequest;
import me.ifmo.backend.dto.library.request.UpdateLibraryRuleRequest;
import me.ifmo.backend.dto.library.response.LibraryRuleResponse;
import me.ifmo.backend.entities.Branch;
import me.ifmo.backend.entities.LibraryRule;
import org.mapstruct.*;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper(uses = BranchMapper.class,
        nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface LibraryRuleMapper {

    @BeanMapping(nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "branch", source = "branch")
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "validFrom", source = "validFrom")
    LibraryRule toEntity(CreateLibraryRuleRequest request, Branch branch, LocalDateTime validFrom);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "branch", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "validFrom", ignore = true)
    void updateEntity(UpdateLibraryRuleRequest request, @MappingTarget LibraryRule rule);

    LibraryRuleResponse toResponse(LibraryRule rule);

    List<LibraryRuleResponse> toResponseList(Collection<LibraryRule> rules);
}
