package com.doc_manag.controller;

import com.doc_manag.dto.DocumentResponse;
import com.doc_manag.dto.PageResponse;
import com.doc_manag.dto.PresignedUrlResponse;
import com.doc_manag.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * REST API. Thin: read the request, call the service, return a DTO.
 *
 * No security yet, so the caller identifies itself with the "X-User-Id" header.
 * Later, Spring Security will provide the user instead.
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private static final String USER_HEADER = "X-User-Id";

    private final DocumentService service;

    /** POST /api/documents   (form-data: file=@report.pdf) */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> upload(@RequestHeader(USER_HEADER) String userId,
                                                   @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.upload(file, userId));
    }

    /** GET /api/documents?page=0&size=20 */
    @GetMapping
    public PageResponse<DocumentResponse> list(@RequestHeader(USER_HEADER) String userId,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return service.list(userId, pageable(page, size));
    }

    /** GET /api/documents/search?name=invoice */
    @GetMapping("/search")
    public PageResponse<DocumentResponse> search(@RequestHeader(USER_HEADER) String userId,
                                                 @RequestParam String name,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return service.search(userId, name, pageable(page, size));
    }

    /** GET /api/documents/{id}   (metadata only) */
    @GetMapping("/{id}")
    public DocumentResponse get(@RequestHeader(USER_HEADER) String userId, @PathVariable UUID id) {
        return service.getById(id, userId);
    }

    /** GET /api/documents/{id}/view   -> pre-signed URL that opens in the browser */
    @GetMapping("/{id}/view")
    public PresignedUrlResponse view(@RequestHeader(USER_HEADER) String userId, @PathVariable UUID id) {
        return service.getViewUrl(id, userId);
    }

    /** GET /api/documents/{id}/download   -> pre-signed URL that downloads with the original name */
    @GetMapping("/{id}/download")
    public PresignedUrlResponse download(@RequestHeader(USER_HEADER) String userId, @PathVariable UUID id) {
        return service.getDownloadUrl(id, userId);
    }

    /** DELETE /api/documents/{id}   (soft delete) */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@RequestHeader(USER_HEADER) String userId, @PathVariable UUID id) {
        service.delete(id, userId);
        return ResponseEntity.noContent().build();
    }

    /** POST /api/documents/{id}/restore */
    @PostMapping("/{id}/restore")
    public DocumentResponse restore(@RequestHeader(USER_HEADER) String userId, @PathVariable UUID id) {
        return service.restore(id, userId);
    }

    private static Pageable pageable(int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);   // cap page size
        return PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
