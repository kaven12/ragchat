package com.example.ragchat.service;

import com.example.ragchat.dto.response.FileListResponse;
import com.example.ragchat.dto.response.FileUploadResponse;
import com.example.ragchat.entity.File;
import com.example.ragchat.exception.BusinessException;
import com.example.ragchat.repository.FileRepository;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final FileRepository fileRepository;
    private final FileValidationService fileValidationService;
    private final MinioClient minioClient;

    @Value("${minio.bucket:files}")
    private String bucketName;

    @Transactional
    public FileUploadResponse uploadFile(MultipartFile file, String userId) {
        // 验证文件
        fileValidationService.validateFile(file);

        String fileId = UUID.randomUUID().toString();
        String storageKey = UUID.randomUUID().toString();
        
        try {
            // 上传到MinIO
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(storageKey)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            // 计算MD5校验和
            String checksum = calculateChecksum(file.getBytes());

            // 保存文件记录
            File fileEntity = File.builder()
                    .id(fileId)
                    .filename(file.getOriginalFilename())
                    .originalFilename(file.getOriginalFilename())
                    .fileSize(file.getSize())
                    .fileType(file.getContentType())
                    .userId(userId)
                    .storageKey(storageKey)
                    .checksum(checksum)
                    .build();
            
            fileRepository.save(fileEntity);
            log.info("File uploaded successfully: {}", fileId);

            return FileUploadResponse.of(fileId, file.getOriginalFilename(), 
                    file.getSize(), storageKey);
        } catch (Exception e) {
            log.error("Failed to upload file", e);
            throw new BusinessException("文件上传失败", e, org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional(readOnly = true)
    public FileListResponse listFiles(String userId, String directoryId, Pageable pageable) {
        Page<File> files;
        if (directoryId != null && !directoryId.isEmpty()) {
            files = fileRepository.findByUserIdAndDirectoryIdAndIsDeletedFalse(userId, directoryId, pageable);
        } else {
            files = fileRepository.findByUserIdAndIsDeletedFalse(userId, pageable);
        }

        return FileListResponse.builder()
                .files(files.getContent().stream()
                        .map(f -> FileListResponse.FileInfo.builder()
                                .id(f.getId())
                                .filename(f.getFilename())
                                .fileSize(f.getFileSize())
                                .fileType(f.getFileType())
                                .directoryId(f.getDirectoryId())
                                .storageKey(f.getStorageKey())
                                .build())
                        .toList())
                .total(files.getTotalElements())
                .page(pageable.getPageNumber())
                .size(pageable.getPageSize())
                .build();
    }

    @Transactional(readOnly = true)
    public InputStream downloadFile(String fileId, String userId) {
        File file = fileRepository.findByIdAndUserIdAndIsDeletedFalse(fileId, userId)
                .orElseThrow(() -> new BusinessException("文件不存在", 
                        org.springframework.http.HttpStatus.NOT_FOUND));

        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(file.getStorageKey())
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to download file", e);
            throw new BusinessException("文件下载失败", e, 
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional
    public void deleteFile(String fileId, String userId) {
        File file = fileRepository.findByIdAndUserIdAndIsDeletedFalse(fileId, userId)
                .orElseThrow(() -> new BusinessException("文件不存在", 
                        org.springframework.http.HttpStatus.NOT_FOUND));

        // 软删除
        file.setIsDeleted(true);
        fileRepository.save(file);
        
        log.info("File marked as deleted: {}", fileId);
    }

    private String calculateChecksum(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not found", e);
        }
    }
}
