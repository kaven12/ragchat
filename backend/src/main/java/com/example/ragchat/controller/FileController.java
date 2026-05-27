package com.example.ragchat.controller;

import com.example.ragchat.dto.response.FileListResponse;
import com.example.ragchat.dto.response.FileUploadResponse;
import com.example.ragchat.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@Slf4j
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping("/upload")
    public ResponseEntity<FileUploadResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        String userId = authentication.getName();
        log.info("User {} uploading file: {}", userId, file.getOriginalFilename());
        
        FileUploadResponse response = fileService.uploadFile(file, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<FileListResponse> listFiles(
            @RequestParam(value = "directoryId", required = false) String directoryId,
            @RequestParam(value = "page", defaultValue = "0") Integer page,
            @RequestParam(value = "size", defaultValue = "20") Integer size,
            Authentication authentication) {
        String userId = authentication.getName();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        FileListResponse response = fileService.listFiles(userId, directoryId, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{fileId}")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable String fileId,
            Authentication authentication) {
        String userId = authentication.getName();
        
        InputStream inputStream = fileService.downloadFile(fileId, userId);
        InputStreamResource resource = new InputStreamResource(inputStream);
        
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment")
                .body(resource);
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<Void> deleteFile(
            @PathVariable String fileId,
            Authentication authentication) {
        String userId = authentication.getName();
        fileService.deleteFile(fileId, userId);
        return ResponseEntity.noContent().build();
    }
}
