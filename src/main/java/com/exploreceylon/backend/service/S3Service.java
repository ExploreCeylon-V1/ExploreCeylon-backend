package com.exploreceylon.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3Service {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.base-url}")
    private String baseUrl;

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    // Maps each accepted content-type to the extension actually written to
    // S3 — the stored file's extension is always derived from this
    // whitelist, never from the client-supplied original filename. That
    // closes an extension-smuggling gap: without this, a file named
    // "payload.php" sent with a spoofed "image/jpeg" content-type header
    // would previously validate and be stored as "folder/uuid.php".
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/jpg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
    );

    /**
     * folder = "destinations" | "gems" | "guides" | "vehicles" | "events" | "profiles"
     */
    public String uploadFile(MultipartFile file, String folder) {
        validateFile(file);

        String fileName = generateFileName(file, folder);

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(request,
                    RequestBody.fromInputStream(
                            file.getInputStream(), file.getSize()));

            String fileUrl = baseUrl + "/" + fileName;
            log.info("File uploaded to S3: {}", fileUrl);
            return fileUrl;

        } catch (IOException e) {
            log.error("S3 upload failed", e);
            throw new RuntimeException(
                    "Failed to upload file: " + e.getMessage());
        }
    }

    public void deleteFile(String fileUrl) {
        try {
            String key = extractKeyFromUrl(fileUrl);

            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.deleteObject(request);
            log.info("File deleted from S3: {}", key);

        } catch (Exception e) {
            log.error("S3 delete failed", e);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "File size exceeds 5MB limit");
        }
        if (!ALLOWED_TYPES.containsKey(file.getContentType())) {
            throw new IllegalArgumentException(
                    "Only JPG, PNG, WEBP images allowed");
        }
    }

    private String generateFileName(MultipartFile file, String folder) {
        // Extension comes from the validated content-type map, not the
        // client-supplied original filename — see the ALLOWED_TYPES comment.
        String extension = ALLOWED_TYPES.get(file.getContentType());
        return folder + "/" + UUID.randomUUID() + extension;
    }

    private String extractKeyFromUrl(String fileUrl) {
        return fileUrl.replace(baseUrl + "/", "");
    }
}