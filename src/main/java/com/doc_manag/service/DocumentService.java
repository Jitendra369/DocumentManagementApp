package com.doc_manag.service;

import com.doc_manag.dto.DocumentResponse;
import com.doc_manag.dto.PageResponse;
import com.doc_manag.dto.PresignedUrlResponse;
import com.doc_manag.entity.Document;
import com.doc_manag.entity.DocumentStatus;
import com.doc_manag.exception.DocumentNotFoundException;
import com.doc_manag.exception.InvalidFileException;
import com.doc_manag.repository.DocumentRepository;
import com.doc_manag.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Business logic. Coordinates validation, S3 (via StorageService) and the database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository repository;
    private final StorageService storage;
    private final FileValidationService validator;

    // ---------------- UPLOAD ----------------
    @Transactional
    public DocumentResponse upload(MultipartFile file, String ownerId) {
        String contentType = validator.validateAndDetectType(file);
        String originalName = cleanFilename(file.getOriginalFilename());
        String s3Key = generateS3Key(originalName);

        // 1) bytes to S3git
        String eTag;
        try (InputStream in = file.getInputStream()) {
            eTag = storage.upload(s3Key, in, file.getSize(), contentType);
        } catch (IOException e) {
            throw new InvalidFileException("Could not read uploaded file");
        }

        // 2) metadata to DB. If this fails, remove the S3 object so it isn't orphaned.
        try {
            Document doc = Document.builder()
                    .originalFilename(originalName)
                    .s3Key(s3Key)
                    .contentType(contentType)
                    .sizeBytes(file.getSize())
                    .checksum(eTag)
                    .ownerId(ownerId)
                    .status(DocumentStatus.ACTIVE)
                    .build();
            // saveAndFlush so DB errors happen here (inside the try), not later at commit
            return DocumentResponse.from(repository.saveAndFlush(doc));
        } catch (RuntimeException e) {
            log.error("DB save failed, removing S3 object {}", s3Key, e);
            storage.delete(s3Key);
            throw e;
        }
    }

    // ---------------- LIST / SEARCH (database only) ----------------
    @Transactional(readOnly = true)
    public PageResponse<DocumentResponse> list(String ownerId, Pageable pageable) {
        Page<Document> page = repository.findByOwnerIdAndStatus(ownerId, DocumentStatus.ACTIVE, pageable);
        return PageResponse.from(page, DocumentResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<DocumentResponse> search(String ownerId, String name, Pageable pageable) {
        Page<Document> page = repository.findByOwnerIdAndStatusAndOriginalFilenameContainingIgnoreCase(
                ownerId, DocumentStatus.ACTIVE, name, pageable);
        return PageResponse.from(page, DocumentResponse::from);
    }

    // ---------------- METADATA ----------------
    @Transactional(readOnly = true)
    public DocumentResponse getById(UUID id, String ownerId) {
        return DocumentResponse.from(findActive(id, ownerId));
    }

    // ---------------- VIEW / DOWNLOAD (pre-signed URLs) ----------------
    @Transactional(readOnly = true)
    public PresignedUrlResponse getViewUrl(UUID id, String ownerId) {
        Document doc = findActive(id, ownerId);   // ownership check happens here
        return storage.generateViewUrl(doc.getS3Key(), doc.getContentType(), doc.getOriginalFilename());
    }

    @Transactional(readOnly = true)
    public PresignedUrlResponse getDownloadUrl(UUID id, String ownerId) {
        Document doc = findActive(id, ownerId);
        return storage.generateDownloadUrl(doc.getS3Key(), doc.getContentType(), doc.getOriginalFilename());
    }

    // ---------------- DELETE (soft) / RESTORE ----------------
    @Transactional
    public void delete(UUID id, String ownerId) {
        Document doc = findActive(id, ownerId);
        doc.setStatus(DocumentStatus.DELETED);
        doc.setDeletedAt(Instant.now());
        // No S3 call here: the file is removed later by a cleanup job (or permanentlyDelete).
    }

    @Transactional
    public DocumentResponse restore(UUID id, String ownerId) {
        Document doc = repository.findByIdAndOwnerIdAndStatus(id, ownerId, DocumentStatus.DELETED)
                .orElseThrow(() -> new DocumentNotFoundException(id));
        doc.setStatus(DocumentStatus.ACTIVE);
        doc.setDeletedAt(null);
        return DocumentResponse.from(doc);
    }

    /** Hard delete: removes the row and the S3 object. Use from a cleanup job or an admin endpoint. */
    @Transactional
    public void permanentlyDelete(Document doc) {
        repository.delete(doc);
        storage.delete(doc.getS3Key());
    }

    // ---------------- helpers ----------------
    private Document findActive(UUID id, String ownerId) {
        return repository.findByIdAndOwnerIdAndStatus(id, ownerId, DocumentStatus.ACTIVE)
                .orElseThrow(() -> new DocumentNotFoundException(id));
    }

    /** documents/2026/11/3f2b...-uuid.pdf  (never the user's filename) */
    private String generateS3Key(String originalName) {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        return "documents/%d/%02d/%s%s".formatted(
                now.getYear(), now.getMonthValue(), UUID.randomUUID(), extensionOf(originalName));
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return "";
        String ext = filename.substring(dot).toLowerCase();
        return ext.matches("\\.[a-z0-9]{1,10}") ? ext : "";
    }

    /** Strip any path the browser may send ("C:\\Users\\me\\file.pdf" -> "file.pdf"). */
    private static String cleanFilename(String name) {
        if (name == null || name.isBlank()) return "unnamed";
        String n = name.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).trim();
        if (n.isEmpty()) return "unnamed";
        return n.length() > 255 ? n.substring(n.length() - 255) : n;
    }
}
