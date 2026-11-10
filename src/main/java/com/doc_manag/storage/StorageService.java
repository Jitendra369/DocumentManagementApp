package com.doc_manag.storage;

import com.doc_manag.dto.PresignedUrlResponse;

import java.io.InputStream;

/**
 * Everything the app needs from file storage.
 * Business code depends on this interface, not on S3 directly,
 * so it can be mocked in tests or swapped for MinIO / local disk.
 */
public interface StorageService {

    /** Uploads bytes and returns the ETag (checksum) S3 gives back. */
    String upload(String key, InputStream data, long size, String contentType);

    void delete(String key);

    boolean exists(String key);

    /** Temporary URL that opens the file in the browser (Content-Disposition: inline). */
    PresignedUrlResponse generateViewUrl(String key, String contentType, String filename);

    /** Temporary URL that downloads the file with its original name (Content-Disposition: attachment). */
    PresignedUrlResponse generateDownloadUrl(String key, String contentType, String filename);
}
