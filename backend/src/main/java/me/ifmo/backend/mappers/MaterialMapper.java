package me.ifmo.backend.mappers;

import me.ifmo.backend.dto.catalog.request.CreateMaterialRequest;
import me.ifmo.backend.dto.catalog.request.UpdateMaterialRequest;
import me.ifmo.backend.dto.catalog.response.MaterialResponse;
import me.ifmo.backend.dto.catalog.response.MaterialShortResponse;
import me.ifmo.backend.entities.Material;
import me.ifmo.backend.entities.MaterialAuthor;
import me.ifmo.backend.entities.MaterialCopy;
import me.ifmo.backend.entities.MaterialGenre;
import org.mapstruct.*;

import java.util.Collection;
import java.util.List;

@Mapper(uses = {MaterialAuthorMapper.class, MaterialGenreMapper.class, MaterialCopyMapper.class})
public interface MaterialMapper {

    @BeanMapping(nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Material toEntity(CreateMaterialRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpdateMaterialRequest request, @MappingTarget Material material);

    @Mapping(target = "authors", source = "materialAuthors")
    @Mapping(target = "genres", source = "materialGenres")
    @Mapping(target = "copies", source = "materialCopies")
    @Mapping(target = "totalCopies", source = "totalCopies")
    @Mapping(target = "availableCopies", source = "availableCopies")
    MaterialResponse toResponse(Material material, Collection<MaterialAuthor> materialAuthors,
                                Collection<MaterialGenre> materialGenres,
                                Collection<MaterialCopy> materialCopies,
                                long totalCopies,
                                long availableCopies);

    default MaterialResponse toResponse(Material material, Collection<MaterialAuthor> materialAuthors,
                                        Collection<MaterialGenre> materialGenres) {
        return toResponse(material, materialAuthors, materialGenres, List.of(), 0, 0);
    }

    @Mapping(target = "authors", source = "materialAuthors")
    @Mapping(target = "genres", source = "materialGenres")
    MaterialShortResponse toShortResponse(Material material, Collection<MaterialAuthor> materialAuthors,
                                          Collection<MaterialGenre> materialGenres);
}
