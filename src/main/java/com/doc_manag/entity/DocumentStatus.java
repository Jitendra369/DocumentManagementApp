package com.doc_manag.entity;

public enum DocumentStatus {
    PENDING,   // row created, file not confirmed in S3 yet (used for pre-signed uploads)
    ACTIVE,    // file is in S3 and visible to the user
    DELETED    // soft-deleted; a cleanup job removes it from S3 later
}
