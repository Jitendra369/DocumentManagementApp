package com.doc_manag.dto;

import com.doc_manag.entity.DocumentCategory;

import java.util.Map;

/** Dashboard numbers: total active documents and the count in each section. */
public record DocumentSummaryResponse(
        long total,
        Map<DocumentCategory, Long> byCategory
) {}
