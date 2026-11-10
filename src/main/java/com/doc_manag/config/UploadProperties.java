package com.doc_manag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.util.List;

/** Binds app.upload.* from application.yml */
@ConfigurationProperties(prefix = "app.upload")
public record UploadProperties(
        DataSize maxSize,              // e.g. 25MB
        List<String> allowedTypes      // MIME types we accept
) {}
