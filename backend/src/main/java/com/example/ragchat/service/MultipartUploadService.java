package com.example.ragchat.service;

import com.example.ragchat.dto.request.CompleteUploadRequest;
import com.example.ragchat.dto.response.InitUploadResponse;
import com.example.ragchat.entity.File;
import com.example.ragchat.exception.BusinessException;
import com.example.ragchat.repository.FileRepository;
import io.minio.*;
import io.minio.errors.MinioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultipartUploadService {

    private final MinioClient minioClient;
    private final FileRepository fileRepository;
    private final FileValidationService fileValidationService;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${minio.bucket:files}")
    private String bucketName;

    @Value("${minio.part-size:52428800}")
    private Integer partSize;

    private static final String UPLOAD_PREFIX = "upload:";
    private static final String UPLOAD_EXPIRE_SECONDS = 3600; // 1小时

    public InitUploadResponse initUpload(String fileName, Long fileSize, String userId) {
        // 验证文件名
        fileValidationService.validateFileName(fileName);

        String uploadId = UUID.randomUUID().toString();
        String storageKey = UUID.randomUUID().toString();

        // 计算总分片数
        int totalParts = (int) Math.ceil((double) fileSize / partSize);

        // 保存上传信息到Redis
        String uploadInfo = String.format("%s:%d:%s", fileName, fileSize, userId);
        redisTemplate.opsForValue().set(UPLOAD_PREFIX + uploadId, uploadInfo, 
                Duration.ofSeconds(UPLOAD_EXPIRE_SECONDS));

        // 生成预签名上传URL
        String uploadUrl = generatePresignedUrl(storageKey);

        log.info("Initialized multipart upload: uploadId={}, storageKey={}", uploadId, storageKey);

        return InitUploadResponse.of(uploadId, storageKey, partSize, totalParts, uploadUrl);
    }

    public void abortUpload(String uploadId) {
        // 从Redis中移除上传信息
        redisTemplate.delete(UPLOAD_PREFIX + uploadId);
        log.info("Aborted multipart upload: {}", uploadId);
    }

    @Transactional
    public String completeUpload(CompleteUploadRequest request, String userId) {
        String uploadId = request.getUploadId();

        // 验证上传信息
        String uploadInfo = redisTemplate.opsForValue().get(UPLOAD_PREFIX + uploadId);
        if (uploadInfo == null) {
            throw new BusinessException("上传会话已过期或不存在", 
                    org.springframework.http.HttpStatus.BAD_REQUEST);
        }

        String[] infoParts = uploadInfo.split(":");
        String fileName = infoParts[0];
        Long fileSize = Long.parseLong(infoParts[1]);
        String storedUserId = infoParts[2];

        if (!storedUserId.equals(userId)) {
            throw new BusinessException("无权访问此上传会话", 
                    org.springframework.http.HttpStatus.FORBIDDEN);
        }

        try {
            // 合并分片
            List<ComposeSource> sources = new ArrayList<>();
            for (CompleteUploadRequest.PartInfo part : request.getParts()) {
                sources.add(ComposeSource.builder()
                        .bucket(bucketName)
                        .object(uploadId + "/" + part.getPartNumber())
                        .build());
            }

            String storageKey = UUID.randomUUID().toString();
            
            minioClient.composeObject(
                    ComposeObjectArgs.builder()
                            .bucket(bucketName)
                            .object(storageKey)
                            .sources(sources)
                            .build()
            );

            // 删除临时分片文件
            for (int i = 0; i < request.getParts().size(); i++) {
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(bucketName)
                                .object(uploadId + "/" + (i + 1))
                                .build()
                );
            }

            // 保存文件记录
            File fileEntity = File.builder()
                    .id(UUID.randomUUID().toString())
                    .filename(fileName)
                    .originalFilename(fileName)
                    .fileSize(fileSize)
                    .fileType(getFileType(fileName))
                    .userId(userId)
                    .storageKey(storageKey)
                    .build();
            
            fileRepository.save(fileEntity);

            // 清理Redis中的上传信息
            redisTemplate.delete(UPLOAD_PREFIX + uploadId);

            log.info("Completed multipart upload: uploadId={}, fileId={}", uploadId, fileEntity.getId());
            return fileEntity.getId();

        } catch (Exception e) {
            log.error("Failed to complete multipart upload", e);
            throw new BusinessException("文件合并失败", e, 
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String generatePresignedUrl(String storageKey) {
        // 生成预签名URL供前端上传分片
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(io.minio.http.Method.PUT)
                            .bucket(bucketName)
                            .object(storageKey)
                            .expiry(UPLOAD_EXPIRE_SECONDS)
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to generate presigned URL", e);
            throw new BusinessException("生成上传URL失败", e, 
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String getFileType(String fileName) {
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "txt" -> "text/plain";
            case "md" -> "text/markdown";
            default -> "application/octet-stream";
        };
    }
}
