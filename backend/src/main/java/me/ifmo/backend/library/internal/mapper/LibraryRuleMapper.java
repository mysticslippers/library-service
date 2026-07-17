package me.ifmo.backend.library.internal.mapper;

import me.ifmo.backend.library.internal.web.request.CreateLibraryRuleRequest;
import me.ifmo.backend.library.internal.web.request.UpdateLibraryRuleRequest;
import me.ifmo.backend.library.internal.web.response.LibraryRuleResponse;
import me.ifmo.backend.library.internal.domain.Branch;
import me.ifmo.backend.library.internal.domain.LibraryRule;
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
    @Mapping(target = "validFrom", ignore = true)
    LibraryRule toEntity(CreateLibraryRuleRequest request, Branch branch);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "branch", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "validFrom", ignore = true)
    void updateEntity(UpdateLibraryRuleRequest request, @MappingTarget LibraryRule rule);

    LibraryRuleResponse toResponse(LibraryRule rule);

    List<LibraryRuleResponse> toResponseList(Collection<LibraryRule> rules);
}
