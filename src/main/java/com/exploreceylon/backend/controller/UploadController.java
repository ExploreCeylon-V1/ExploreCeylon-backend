package com.exploreceylon.backend.controller;

import com.exploreceylon.backend.dto.UploadResponse;
import com.exploreceylon.backend.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/upload")
@RequiredArgsConstructor
public class UploadController {

    private final S3Service s3Service;

    // POST /api/v1/upload/single?folder=destinations
    @PostMapping("/single")
    public ResponseEntity<UploadResponse> uploadSingle(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "general") String folder) {

        String url = s3Service.uploadFile(file, folder);

        return ResponseEntity.ok(UploadResponse.builder()
                .imageUrl(url)
                .fileName(file.getOriginalFilename())
                .success(true)
                .message("Upload successful")
                .build());
    }

    // POST /api/v1/upload/multiple?folder=destinations
    @PostMapping("/multiple")
    public ResponseEntity<List<UploadResponse>> uploadMultiple(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(defaultValue = "general") String folder) {

        List<UploadResponse> responses = files.stream()
                .map(file -> {
                    String url = s3Service.uploadFile(file, folder);
                    return UploadResponse.builder()
                            .imageUrl(url)
                            .fileName(file.getOriginalFilename())
                            .success(true)
                            .message("Upload successful")
                            .build();
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    // DELETE /api/v1/upload?imageUrl=...
    @DeleteMapping
    public ResponseEntity<Void> deleteImage(
            @RequestParam String imageUrl) {
        s3Service.deleteFile(imageUrl);
        return ResponseEntity.ok().build();
    }
}