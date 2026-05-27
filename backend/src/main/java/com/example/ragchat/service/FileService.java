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
        long startTime = System.currentTimeMillis();
        log.info("[FileService.uploadFile] 方法开始 | userId={}, fileName={}, fileSize={}",
                userId, file.getOriginalFilename(), file.getSize());

        try {
            // 验证文件
            log.debug("[FileService.uploadFile] 开始验证文件 | fileName={}", file.getOriginalFilename());
            fileValidationService.validateFile(file);
            log.debug("[FileService.uploadFile] 文件验证通过 | fileName={}", file.getOriginalFilename());

            String fileId = UUID.randomUUID().toString();
            String storageKey = UUID.randomUUID().toString();

            log.debug("[FileService.uploadFile] 上传到MinIO | fileId={}, storageKey={}, bucket={}",
                    fileId, storageKey, bucketName);

            // 上传到MinIO
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(storageKey)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            log.debug("[FileService.uploadFile] MinIO上传完成 | storageKey={}", storageKey);

            // 计算MD5校验和
            log.debug("[FileService.uploadFile] 计算文件MD5 | fileId={}", fileId);
            String checksum = calculateChecksum(file.getBytes());
            log.debug("[FileService.uploadFile] MD5计算完成 | fileId={}, checksum={}", fileId, checksum);

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
            log.info("[FileService.uploadFile] 文件上传成功 | fileId={}, fileName={}, userId={}, fileSize={}",
                    fileId, file.getOriginalFilename(), userId, file.getSize());

            long costTime = System.currentTimeMillis() - startTime;
            log.debug("[FileService.uploadFile] 方法执行完成 | fileId={}, 耗时={}ms", fileId, costTime);

            return FileUploadResponse.of(fileId, file.getOriginalFilename(),
                    file.getSize(), storageKey);

        } catch (BusinessException e) {
            log.error("[FileService.uploadFile] 业务异常 | userId={}, fileName={}, error={}",
                    userId, file.getOriginalFilename(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("[FileService.uploadFile] 系统异常 | userId={}, fileName={}, error={}, stackTrace={}",
                    userId, file.getOriginalFilename(), e.getMessage(), e.getStackTrace());
            throw new BusinessException("文件上传失败", e, org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional(readOnly = true)
    public FileListResponse listFiles(String userId, String directoryId, Pageable pageable) {
        long startTime = System.currentTimeMillis();
        log.info("[FileService.listFiles] 方法开始 | userId={}, directoryId={}, page={}, size={}",
                userId, directoryId, pageable.getPageNumber(), pageable.getPageSize());

        try {
            Page<File> files;
            if (directoryId != null && !directoryId.isEmpty()) {
                log.debug("[FileService.listFiles] 查询指定目录文件 | userId={}, directoryId={}", userId, directoryId);
                files = fileRepository.findByUserIdAndDirectoryIdAndIsDeletedFalse(userId, directoryId, pageable);
            } else {
                log.debug("[FileService.listFiles] 查询用户所有文件 | userId={}", userId);
                files = fileRepository.findByUserIdAndIsDeletedFalse(userId, pageable);
            }

            log.info("[FileService.listFiles] 文件列表查询成功 | userId={}, totalElements={}, totalPages={}",
                    userId, files.getTotalElements(), files.getTotalPages());

            long costTime = System.currentTimeMillis() - startTime;
            log.debug("[FileService.listFiles] 方法执行完成 | userId={}, 耗时={}ms", userId, costTime);

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

        } catch (Exception e) {
            log.error("[FileService.listFiles] 系统异常 | userId={}, error={}, stackTrace={}",
                    userId, e.getMessage(), e.getStackTrace());
            throw new BusinessException("获取文件列表失败", e, org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional(readOnly = true)
    public InputStream downloadFile(String fileId, String userId) {
        long startTime = System.currentTimeMillis();
        log.info("[FileService.downloadFile] 方法开始 | fileId={}, userId={}", fileId, userId);

        try {
            File file = fileRepository.findByIdAndUserIdAndIsDeletedFalse(fileId, userId)
                    .orElseThrow(() -> {
                        log.error("[FileService.downloadFile] 文件不存在 | fileId={}, userId={}", fileId, userId);
                        return new BusinessException("文件不存在",
                                org.springframework.http.HttpStatus.NOT_FOUND);
                    });

            log.debug("[FileService.downloadFile] 开始下载文件 | fileId={}, storageKey={}, fileName={}",
                    fileId, file.getStorageKey(), file.getFilename());

            InputStream inputStream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(file.getStorageKey())
                            .build()
            );

            log.info("[FileService.downloadFile] 文件下载成功 | fileId={}, userId={}, fileName={}",
                    fileId, userId, file.getFilename());

            long costTime = System.currentTimeMillis() - startTime;
            log.debug("[FileService.downloadFile] 方法执行完成 | fileId={}, 耗时={}ms", fileId, costTime);

            return inputStream;

        } catch (BusinessException e) {
            log.error("[FileService.downloadFile] 业务异常 | fileId={}, userId={}, error={}",
                    fileId, userId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("[FileService.downloadFile] 系统异常 | fileId={}, userId={}, error={}, stackTrace={}",
                    fileId, userId, e.getMessage(), e.getStackTrace());
            throw new BusinessException("文件下载失败", e,
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional
    public void deleteFile(String fileId, String userId) {
        long startTime = System.currentTimeMillis();
        log.info("[FileService.deleteFile] 方法开始 | fileId={}, userId={}", fileId, userId);

        try {
            File file = fileRepository.findByIdAndUserIdAndIsDeletedFalse(fileId, userId)
                    .orElseThrow(() -> {
                        log.error("[FileService.deleteFile] 文件不存在 | fileId={}, userId={}", fileId, userId);
                        return new BusinessException("文件不存在",
                                org.springframework.http.HttpStatus.NOT_FOUND);
                    });

            log.debug("[FileService.deleteFile] 执行软删除 | fileId={}, fileName={}", fileId, file.getFilename());

            // 软删除
            file.setIsDeleted(true);
            fileRepository.save(file);

            log.info("[FileService.deleteFile] 文件删除成功 | fileId={}, userId={}, fileName={}",
                    fileId, userId, file.getFilename());

            long costTime = System.currentTimeMillis() - startTime;
            log.debug("[FileService.deleteFile] 方法执行完成 | fileId={}, 耗时={}ms", fileId, costTime);

        } catch (BusinessException e) {
            log.error("[FileService.deleteFile] 业务异常 | fileId={}, userId={}, error={}",
                    fileId, userId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("[FileService.deleteFile] 系统异常 | fileId={}, userId={}, error={}, stackTrace={}",
                    fileId, userId, e.getMessage(), e.getStackTrace());
            throw new BusinessException("文件删除失败", e, org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        }
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
            log.error("[FileService.calculateChecksum] MD5算法不存在", e);
            throw new RuntimeException("MD5 algorithm not found", e);
        }
    }
}
