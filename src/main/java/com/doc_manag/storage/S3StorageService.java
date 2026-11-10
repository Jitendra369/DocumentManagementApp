package com.doc_manag.storage;

import com.doc_manag.config.S3Properties;
import com.doc_manag.dto.PresignedUrlResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * The ONLY class that talks to the AWS SDK. No business rules here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class S3StorageService implements StorageService {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final S3Properties props;

    @Override
    public String upload(String key, InputStream data, long size, String contentType) {
        log.info("Uploading object to S3: bucket={}, key={}, size={} bytes, contentType={}",
                props.bucket(), key, size, contentType);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(props.bucket())
                .key(key)
                .contentType(contentType)
                .serverSideEncryption(ServerSideEncryption.AES256)   // encrypt at rest
                .build();

        try {
            PutObjectResponse response = s3.putObject(request, RequestBody.fromInputStream(data, size));
            log.info("Uploaded object to S3: key={}, size={} bytes, eTag={}", key, size, response.eTag());
            return response.eTag();
        } catch (SdkException e) {
            log.error("Failed to upload object to S3: bucket={}, key={}", props.bucket(), key, e);
            throw e;
        }
    }

    @Override
    public void delete(String key) {
        log.info("Deleting object from S3: bucket={}, key={}", props.bucket(), key);
        try {
            s3.deleteObject(b -> b.bucket(props.bucket()).key(key));
            log.info("Deleted object from S3: key={}", key);
        } catch (SdkException e) {
            log.error("Failed to delete object from S3: bucket={}, key={}", props.bucket(), key, e);
            throw e;
        }
    }

    @Override
    public boolean exists(String key) {
        log.debug("Checking if object exists in S3: bucket={}, key={}", props.bucket(), key);
        try {
            s3.headObject(b -> b.bucket(props.bucket()).key(key));
            log.debug("Object exists in S3: key={}", key);
            return true;
        } catch (NoSuchKeyException e) {
            log.debug("Object does not exist in S3: key={}", key);
            return false;
        } catch (SdkException e) {
            log.error("Failed to check object existence in S3: bucket={}, key={}", props.bucket(), key, e);
            throw e;
        }
    }

    @Override
    public PresignedUrlResponse generateViewUrl(String key, String contentType, String filename) {
        log.info("Generating presigned view URL: key={}, contentType={}, filename={}", key, contentType, filename);
        String disposition = ContentDisposition.inline()
                .filename(filename, StandardCharsets.UTF_8)
                .build()
                .toString();
        return presignGet(key, contentType, disposition);
    }

    @Override
    public PresignedUrlResponse generateDownloadUrl(String key, String contentType, String filename) {
        log.info("Generating presigned download URL: key={}, contentType={}, filename={}", key, contentType, filename);
        String disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build()
                .toString();
        return presignGet(key, contentType, disposition);
    }

    private PresignedUrlResponse presignGet(String key, String contentType, String disposition) {
        log.debug("Presigning GET request: bucket={}, key={}, validFor={} min, disposition={}",
                props.bucket(), key, props.presignMinutes(), disposition);
        try {
            PresignedGetObjectRequest presigned = presigner.presignGetObject(r -> r
                    .signatureDuration(Duration.ofMinutes(props.presignMinutes()))
                    .getObjectRequest(g -> g
                            .bucket(props.bucket())
                            .key(key)
                            // S3 sends these headers back when the URL is opened
                            .responseContentType(contentType)
                            .responseContentDisposition(disposition)));

            // Never log the URL itself: it is a bearer credential until it expires
            log.debug("Presigned GET request generated: key={}, expiresAt={}", key, presigned.expiration());
            return new PresignedUrlResponse(presigned.url().toString(), presigned.expiration());
        } catch (SdkException e) {
            log.error("Failed to presign GET request: bucket={}, key={}", props.bucket(), key, e);
            throw e;
        }
    }
}
