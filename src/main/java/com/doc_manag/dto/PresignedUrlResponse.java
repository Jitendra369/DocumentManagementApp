package com.doc_manag.dto;

import java.time.Instant;

public record PresignedUrlResponse(String url, Instant expiresAt) {}
