package com.example.ragchat.controller;

import com.example.ragchat.dto.request.CompleteUploadRequest;
import com.example.ragchat.dto.request.InitUploadRequest;
import com.example.ragchat.dto.response.InitUploadResponse;
import com.example.ragchat.service.MultipartUploadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/files/multipart")
@RequiredArgsConstructor
public class MultipartUploadController {

    private final MultipartUploadService multipartUploadService;

    @PostMapping("/init")
    public ResponseEntity<InitUploadResponse> initUpload(
            @Valid @RequestBody InitUploadRequest request,
            Authentication authentication) {
        String userId = authentication.getName();
        log.info("User {} initializing multipart upload for: {}", userId, request.getFileName());
        
        InitUploadResponse response = multipartUploadService.initUpload(
                request.getFileName(), request.getFileSize(), userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{uploadId}/complete")
    public ResponseEntity<Map<String, String>> completeUpload(
            @PathVariable String uploadId,
            @Valid @RequestBody CompleteUploadRequest request,
            Authentication authentication) {
        String userId = authentication.getName();
        request.setUploadId(uploadId);
        
        String fileId = multipartUploadService.completeUpload(request, userId);
        return ResponseEntity.ok(Map.of("fileId", fileId));
    }

    @PostMapping("/{uploadId}/abort")
    public ResponseEntity<Void> abortUpload(@PathVariable String uploadId) {
        multipartUploadService.abortUpload(uploadId);
        return ResponseEntity.ok().build();
    }
}
