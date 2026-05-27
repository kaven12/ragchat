package com.example.ragchat.service;

import com.example.ragchat.dto.request.CompleteUploadRequest;
import com.example.ragchat.dto.response.InitUploadResponse;
import com.example.ragchat.entity.File;
import com.example.ragchat.exception.BusinessException;
import com.example.ragchat.repository.FileRepository;
import io.minio.ComposeSource;
import io.minio.ComposeObjectArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private static final String UPLOAD_EXPIRE_SECONDS = 3600;

    public InitUploadResponse initUpload(String fileName, Long fileSize, String userId) {
        long startTime = System.currentTimeMillis();
        log.info("[MultipartUploadService.initUpload] 方法开始 | userId={}, fileName={}, fileSize={}",
                userId, fileName, fileSize);

        try {
            // 验证文件名
            log.debug("[MultipartUploadService.initUpload] 验证文件名 | fileName={}", fileName);
            fileValidationService.validateFileName(fileName);

            String uploadId = UUID.randomUUID().toString();
            String storageKey = UUID.randomUUID().toString();

            // 计算总分片数
            int totalParts = (int) Math.ceil((double) fileSize / partSize);
            log.debug("[MultipartUploadService.initUpload] 分片计算 | totalSize={}, partSize={}, totalParts={}",
                    fileSize, partSize, totalParts);

            // 保存上传信息到Redis
            String uploadInfo = String.format("%s:%d:%s", fileName, fileSize, userId);
            String redisKey = UPLOAD_PREFIX + uploadId;
            redisTemplate.opsForValue().set(redisKey, uploadInfo, Duration.ofSeconds(UPLOAD_EXPIRE_SECONDS));
            log.debug("[MultipartUploadService.initUpload] 上传信息已存储到Redis | key={}, expireSeconds={}",
                    redisKey, UPLOAD_EXPIRE_SECONDS);

            // 生成预签名上传URL
            String uploadUrl = generatePresignedUrl(storageKey);
            log.debug("[MultipartUploadService.initUpload] 预签名URL生成完成 | uploadId={}", uploadId);

            log.info("[MultipartUploadService.initUpload] 分片上传初始化成功 | uploadId={}, storageKey={}, fileName={}, totalParts={}",
                    uploadId, storageKey, fileName, totalParts);

            long costTime = System.currentTimeMillis() - startTime;
            log.debug("[MultipartUploadService.initUpload] 方法执行完成 | uploadId={}, 耗时={}ms", uploadId, costTime);

            return InitUploadResponse.of(uploadId, storageKey, partSize, totalParts, uploadUrl);

        } catch (BusinessException e) {
            log.error("[MultipartUploadService.initUpload] 业务异常 | userId={}, fileName={}, error={}",
                    userId, fileName, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("[MultipartUploadService.initUpload] 系统异常 | userId={}, fileName={}, error={}, stackTrace={}",
                    userId, fileName, e.getMessage(), e.getStackTrace());
            throw new BusinessException("分片上传初始化失败", e,
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public void abortUpload(String uploadId) {
        log.info("[MultipartUploadService.abortUpload] 方法开始 | uploadId={}", uploadId);

        try {
            String redisKey = UPLOAD_PREFIX + uploadId;
            redisTemplate.delete(redisKey);
            log.info("[MultipartUploadService.abortUpload] 分片上传已取消 | uploadId={}", uploadId);
        } catch (Exception e) {
            log.error("[MultipartUploadService.abortUpload] 取消上传异常 | uploadId={}, error={}",
                    uploadId, e.getMessage());
        }
    }

    @Transactional
    public String completeUpload(CompleteUploadRequest request, String userId) {
        long startTime = System.currentTimeMillis();
        String uploadId = request.getUploadId();
        log.info("[MultipartUploadService.completeUpload] 方法开始 | uploadId={}, userId={}, partsCount={}",
                uploadId, userId, request.getParts() != null ? request.getParts().size() : 0);

        try {
            // 验证上传信息
            String redisKey = UPLOAD_PREFIX + uploadId;
            String uploadInfo = redisTemplate.opsForValue().get(redisKey);

            if (uploadInfo == null) {
                log.error("[MultipartUploadService.completeUpload] 上传会话不存在或已过期 | uploadId={}", uploadId);
                throw new BusinessException("上传会话已过期或不存在",
                        org.springframework.http.HttpStatus.BAD_REQUEST);
            }

            String[] infoParts = uploadInfo.split(":");
            String fileName = infoParts[0];
            Long fileSize = Long.parseLong(infoParts[1]);
            String storedUserId = infoParts[2];

            log.debug("[MultipartUploadService.completeUpload] 上传信息验证 | uploadId={}, fileName={}, fileSize={}, storedUserId={}",
                    uploadId, fileName, fileSize, storedUserId);

            if (!storedUserId.equals(userId)) {
                log.error("[MultipartUploadService.completeUpload] 用户无权访问此上传会话 | uploadId={}, userId={}, storedUserId={}",
                        uploadId, userId, storedUserId);
                throw new BusinessException("无权访问此上传会话",
                        org.springframework.http.HttpStatus.FORBIDDEN);
            }

            // 构建分片列表
            List<ComposeSource> sources = new ArrayList<>();
            for (CompleteUploadRequest.PartInfo part : request.getParts()) {
                log.debug("[MultipartUploadService.completeUpload] 添加分片 | uploadId={}, partNumber={}, etag={}",
                        uploadId, part.getPartNumber(), part.getEtag());
                sources.add(ComposeSource.builder()
                        .bucket(bucketName)
                        .object(uploadId + "/" + part.getPartNumber())
                        .build());
            }

            String storageKey = UUID.randomUUID().toString();
            log.debug("[MultipartUploadService.completeUpload] 开始合并分片 | uploadId={}, storageKey={}, sourcesCount={}",
                    uploadId, storageKey, sources.size());

            // 合并分片
            minioClient.composeObject(
                    ComposeObjectArgs.builder()
                            .bucket(bucketName)
                            .object(storageKey)
                            .sources(sources)
                            .build()
            );
            log.debug("[MultipartUploadService.completeUpload] 分片合并完成 | storageKey={}", storageKey);

            // 删除临时分片文件
            log.debug("[MultipartUploadService.completeUpload] 开始删除临时分片 | uploadId={}, partsCount={}",
                    uploadId, request.getParts().size());

            for (int i = 0; i < request.getParts().size(); i++) {
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(bucketName)
                                .object(uploadId + "/" + (i + 1))
                                .build()
                );
            }
            log.debug("[MultipartUploadService.completeUpload] 临时分片删除完成 | uploadId={}", uploadId);

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
            log.debug("[MultipartUploadService.completeUpload] 文件记录已保存 | fileId={}", fileEntity.getId());

            // 清理Redis中的上传信息
            redisTemplate.delete(redisKey);
            log.debug("[MultipartUploadService.completeUpload] Redis上传信息已清理 | uploadId={}", uploadId);

            log.info("[MultipartUploadService.completeUpload] 分片上传完成 | uploadId={}, fileId={}, fileName={}, fileSize={}",
                    uploadId, fileEntity.getId(), fileName, fileSize);

            long costTime = System.currentTimeMillis() - startTime;
            log.debug("[MultipartUploadService.completeUpload] 方法执行完成 | uploadId={}, 耗时={}ms", uploadId, costTime);

            return fileEntity.getId();

        } catch (BusinessException e) {
            log.error("[MultipartUploadService.completeUpload] 业务异常 | uploadId={}, error={}", uploadId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("[MultipartUploadService.completeUpload] 系统异常 | uploadId={}, error={}, stackTrace={}",
                    uploadId, e.getMessage(), e.getStackTrace());
            throw new BusinessException("文件合并失败", e,
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String generatePresignedUrl(String storageKey) {
        try {
            String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucketName)
                            .object(storageKey)
                            .expiry(UPLOAD_EXPIRE_SECONDS)
                            .build()
            );
            log.debug("[MultipartUploadService.generatePresignedUrl] 预签名URL生成 | storageKey={}, url长度={}",
                    storageKey, url.length());
            return url;
        } catch (Exception e) {
            log.error("[MultipartUploadService.generatePresignedUrl] 生成预签名URL失败 | storageKey={}, error={}",
                    storageKey, e.getMessage());
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
