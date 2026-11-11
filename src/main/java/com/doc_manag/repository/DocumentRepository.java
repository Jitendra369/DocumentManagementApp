package com.doc_manag.repository;

import com.doc_manag.entity.Document;
import com.doc_manag.entity.DocumentCategory;
import com.doc_manag.entity.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    /** One document, only if it belongs to this owner and has this status. */
    Optional<Document> findByIdAndOwnerIdAndStatus(UUID id, String ownerId, DocumentStatus status);

    /** "My documents" list, paginated. */
    Page<Document> findByOwnerIdAndStatus(String ownerId, DocumentStatus status, Pageable pageable);

    /** "My documents" in one section, paginated. */
    Page<Document> findByOwnerIdAndStatusAndCategory(
            String ownerId, DocumentStatus status, DocumentCategory category, Pageable pageable);

    /** Search by filename (case-insensitive, partial match). */
    Page<Document> findByOwnerIdAndStatusAndOriginalFilenameContainingIgnoreCase(
            String ownerId, DocumentStatus status, String name, Pageable pageable);

    /** Search by filename within one section. */
    Page<Document> findByOwnerIdAndStatusAndCategoryAndOriginalFilenameContainingIgnoreCase(
            String ownerId, DocumentStatus status, DocumentCategory category, String name, Pageable pageable);

    /** Dashboard: number of documents per section. */
    @Query("""
            select d.category as category, count(d) as count
            from Document d
            where d.ownerId = :ownerId and d.status = :status
            group by d.category
            """)
    List<CategoryCount> countByCategory(@Param("ownerId") String ownerId, @Param("status") DocumentStatus status);

    /** For a cleanup job: soft-deleted documents older than a given time. */
    List<Document> findByStatusAndDeletedAtBefore(DocumentStatus status, Instant before);

    interface CategoryCount {
        DocumentCategory getCategory();
        long getCount();
    }
}
