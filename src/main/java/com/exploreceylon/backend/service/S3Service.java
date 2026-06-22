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

    private static final String[] ALLOWED_TYPES = {
            "image/jpeg", "image/jpg", "image/png", "image/webp"
    };

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

        String contentType = file.getContentType();
        boolean validType = false;
        for (String type : ALLOWED_TYPES) {
            if (type.equals(contentType)) {
                validType = true;
                break;
            }
        }
        if (!validType) {
            throw new IllegalArgumentException(
                    "Only JPG, PNG, WEBP images allowed");
        }
    }

    private String generateFileName(MultipartFile file, String folder) {
        String originalName = file.getOriginalFilename();
        String extension = originalName != null && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf("."))
                : ".jpg";

        return folder + "/" + UUID.randomUUID() + extension;
    }

    private String extractKeyFromUrl(String fileUrl) {
        return fileUrl.replace(baseUrl + "/", "");
    }
}