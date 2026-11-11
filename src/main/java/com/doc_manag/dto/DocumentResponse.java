package com.doc_manag.dto;

import com.doc_manag.entity.Document;

import java.time.Instant;
import java.util.UUID;

/** What the API returns for a document. Note: no s3Key, no bucket. */
public record DocumentResponse(
        UUID id,
        String filename,
        String contentType,
        long sizeBytes,
        String status,
        String category,
        Instant createdAt
) {
    public static DocumentResponse from(Document d) {
        return new DocumentResponse(
                d.getId(),
                d.getOriginalFilename(),
                d.getContentType(),
                d.getSizeBytes(),
                d.getStatus().name(),
                d.getCategory().name(),
                d.getCreatedAt());
    }
}
