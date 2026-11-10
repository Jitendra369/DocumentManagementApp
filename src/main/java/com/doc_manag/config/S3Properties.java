package com.doc_manag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds app.s3.* from application.yml (values come from environment variables) */
@ConfigurationProperties(prefix = "app.s3")
public record S3Properties(
        String bucket,
        String region,
        String endpoint,          // only set for LocalStack/MinIO
        boolean pathStyle,
        long presignMinutes,
        boolean createBucketIfMissing,
        String accessKey,         // optional: falls back to the AWS default credentials chain
        String secretKey
) {
    public boolean hasCustomEndpoint() {
        return endpoint != null && !endpoint.isBlank();
    }

    public boolean hasStaticCredentials() {
        return accessKey != null && !accessKey.isBlank()
                && secretKey != null && !secretKey.isBlank();
    }

    /** Keep the secret out of logs if this record is ever printed. */
    @Override
    public String toString() {
        return "S3Properties[bucket=%s, region=%s, endpoint=%s, pathStyle=%s, presignMinutes=%d, createBucketIfMissing=%s, staticCredentials=%s]"
                .formatted(bucket, region, endpoint, pathStyle, presignMinutes, createBucketIfMissing, hasStaticCredentials());
    }
}
