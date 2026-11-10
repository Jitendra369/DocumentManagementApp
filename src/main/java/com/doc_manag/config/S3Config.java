package com.doc_manag.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * Creates the S3 client beans ONCE. They are thread-safe and reused everywhere.
 */
@Slf4j
@Configuration
public class S3Config {

    /**
     * Uses AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY when provided (env vars or .env),
     * otherwise the default chain (IAM role, ~/.aws/credentials, ...).
     */
    @Bean
    public AwsCredentialsProvider awsCredentialsProvider(S3Properties props) {
        if (props.hasStaticCredentials()) {
            log.info("Using AWS credentials from environment configuration");
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(props.accessKey(), props.secretKey()));
        }
        if (props.hasCustomEndpoint()) {
            // LocalStack/MinIO accept any credentials; lets local dev run with zero setup
            log.info("No AWS credentials configured; using LocalStack dummy credentials for {}", props.endpoint());
            return StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"));
        }
        log.info("No static AWS credentials configured; using the default credentials chain");
        return DefaultCredentialsProvider.builder().build();
    }

    @Bean
    public S3Client s3Client(S3Properties props, AwsCredentialsProvider credentials) {
        var builder = S3Client.builder()
                .region(Region.of(props.region()))
                .credentialsProvider(credentials)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(props.pathStyle())
                        .build());

        if (props.hasCustomEndpoint()) {
            builder.endpointOverride(URI.create(props.endpoint()));
        }
        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner(S3Properties props, AwsCredentialsProvider credentials) {
        var builder = S3Presigner.builder()
                .region(Region.of(props.region()))
                .credentialsProvider(credentials)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(props.pathStyle())
                        .build());

        if (props.hasCustomEndpoint()) {
            builder.endpointOverride(URI.create(props.endpoint()));
        }
        return builder.build();
    }

    /** Dev convenience: create the bucket on startup if it doesn't exist. Turn off in prod. */
    @Bean
    public ApplicationRunner createBucketIfMissing(S3Client s3, S3Properties props) {
        return args -> {
            if (!props.createBucketIfMissing()) {
                return;
            }
            try {
                s3.headBucket(b -> b.bucket(props.bucket()));
                log.info("S3 bucket '{}' is ready", props.bucket());
            } catch (NoSuchBucketException e) {
                s3.createBucket(b -> b.bucket(props.bucket()));
                log.info("Created S3 bucket '{}'", props.bucket());
            }
        };
    }
}
