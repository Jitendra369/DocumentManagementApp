package com.doc_manag.service;

import com.doc_manag.config.UploadProperties;
import com.doc_manag.exception.InvalidFileException;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Checks an upload BEFORE it goes to S3: not empty, not too big, allowed real type.
 */
@Service
@RequiredArgsConstructor
public class FileValidationService {

    private final UploadProperties uploadProps;
    private final Tika tika = new Tika();   // thread-safe, reuse one instance

    /** @return the real MIME type detected from the file's content */
    public String validateAndDetectType(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("File is empty");
        }

        if (file.getSize() > uploadProps.maxSize().toBytes()) {
            throw new InvalidFileException("File is larger than " + uploadProps.maxSize().toMegabytes() + " MB");
        }

        String detectedType = detectType(file);

        if (!uploadProps.allowedTypes().contains(detectedType)) {
            throw new InvalidFileException("File type not allowed: " + detectedType);
        }
        return detectedType;
    }

    private String detectType(MultipartFile file) {
        // Tika reads the first bytes ("magic bytes"); the filename only refines the result
        // (e.g. a ZIP signature + ".docx" name -> Word document).
        try (InputStream in = new BufferedInputStream(file.getInputStream())) {
            return tika.detect(in, file.getOriginalFilename());
        } catch (IOException e) {
            throw new InvalidFileException("Could not read file");
        }
    }
}
