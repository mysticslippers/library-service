package me.ifmo.backend.catalog.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.catalog.web.request.ChangeMaterialStatusRequest;
import me.ifmo.backend.catalog.web.request.CreateMaterialRequest;
import me.ifmo.backend.catalog.web.request.MaterialSearchRequest;
import me.ifmo.backend.catalog.web.request.UpdateMaterialRequest;
import me.ifmo.backend.catalog.web.response.MaterialResponse;
import me.ifmo.backend.catalog.web.response.MaterialCoverResponse;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.catalog.application.MaterialCoverService;
import me.ifmo.backend.catalog.application.MaterialService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.InputStreamResource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

@RestController
@RequestMapping("/materials")
@RequiredArgsConstructor
@Tag(name = "Materials", description = "Search and management of catalog materials")
public class MaterialController {

    private final MaterialService service;
    private final MaterialCoverService coverService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    @Operation(summary = "Create a catalog material")
    public MaterialResponse create(@Valid @RequestBody CreateMaterialRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a material by ID")
    public MaterialResponse getMaterialById(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return service.getMaterialById(Long.valueOf(userDetails.getUsername()), id);
    }

    @GetMapping("/isbn/{isbn}")
    @Operation(summary = "Get a material by ISBN")
    public MaterialResponse getMaterialByIsbn(@AuthenticationPrincipal UserDetails userDetails, @PathVariable String isbn) {
        return service.getMaterialByIsbn(Long.valueOf(userDetails.getUsername()), isbn);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    @Operation(summary = "Update a material")
    public MaterialResponse update(@PathVariable Long id, @Valid @RequestBody UpdateMaterialRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    @Operation(summary = "Change a material's status")
    public MaterialResponse changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeMaterialStatusRequest request) {
        return service.changeStatus(id, request);
    }

    @GetMapping
    @Operation(summary = "Search catalog materials")
    public PageResponse<MaterialResponse> search(@AuthenticationPrincipal UserDetails userDetails, @Valid @ModelAttribute MaterialSearchRequest request, @ParameterObject Pageable pageable) {
        return service.search(Long.valueOf(userDetails.getUsername()), request, pageable);
    }

    @PutMapping(value = "/{id}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    @Operation(summary = "Upload or replace a material cover")
    public MaterialCoverResponse uploadCover(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file
    ) {
        return coverService.upload(id, file);
    }

    @GetMapping("/{id}/cover")
    @Operation(summary = "Download the public cover of an active material")
    @ApiResponse(
            responseCode = "200",
            description = "Cover image",
            content = @Content(
                    mediaType = "image/*",
                    schema = @Schema(type = "string", format = "binary")
            )
    )
    public ResponseEntity<InputStreamResource> getCover(@PathVariable Long id) {
        var cover = coverService.getPublic(id);
        var response = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(cover.contentType()))
                .contentLength(cover.contentLength())
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());

        if (cover.etag() != null)
            response.eTag(quotedEtag(cover.etag()));

        if (cover.lastModified() != null)
            response.lastModified(cover.lastModified());

        return response.body(new InputStreamResource(cover.content()));
    }

    @DeleteMapping("/{id}/cover")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    @Operation(summary = "Delete a material cover")
    public void deleteCover(@PathVariable Long id) {
        coverService.delete(id);
    }

    private String quotedEtag(String etag) {
        return etag.startsWith("\"") ? etag : "\"%s\"".formatted(etag);
    }
}
