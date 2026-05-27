package com.example.ragchat.mcp;

import com.example.ragchat.entity.File;
import com.example.ragchat.exception.BusinessException;
import com.example.ragchat.repository.FileRepository;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileMCP {

    private final FileRepository fileRepository;
    private final MinioClient minioClient;

    @Value("${minio.bucket:files}")
    private String bucketName;

    public List<File> listFilesByUserId(String userId) {
        return fileRepository.findByUserIdAndIsDeletedFalse(userId, 
                org.springframework.data.domain.Pageable.unpaged()).getContent();
    }

    public InputStream getFileContent(String storageKey) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(storageKey)
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to get file content from MinIO", e);
            throw new BusinessException("获取文件内容失败", e, 
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public File getFileById(String fileId) {
        return fileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException("文件不存在", 
                        org.springframework.http.HttpStatus.NOT_FOUND));
    }
}
