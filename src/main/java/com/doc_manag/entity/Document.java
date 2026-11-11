package com.doc_manag.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata for one uploaded file. The bytes live in S3 at {@link #s3Key}.
 */
@Entity
@Table(name = "documents")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Name the user uploaded, e.g. "Salary Slip Oct.pdf". Used for display and downloads. */
    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    /** Where the file is in S3, e.g. "documents/2026/11/7f3a...pdf". Never sent to the client. */
    @Column(name = "s3_key", nullable = false, unique = true, length = 512)
    private String s3Key;

    /** Real type detected by Tika, e.g. "application/pdf". */
    @Column(name = "content_type", nullable = false, length = 150)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** ETag returned by S3 after upload. */
    @Column(name = "checksum", length = 100)
    private String checksum;

    @Column(name = "owner_id", nullable = false, length = 100)
    private String ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DocumentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private DocumentCategory category;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
