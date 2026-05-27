package com.example.ragchat.service;

import com.example.ragchat.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.Set;

@Slf4j
@Service
public class FileValidationService {

    // 允许的文件类型白名单
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md"
    );

    // 允许的MIME类型
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain",
            "text/markdown"
    );

    @Value("${spring.servlet.multipart.max-file-size:100MB}")
    private String maxFileSize;

    public void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new BusinessException("文件名不能为空");
        }

        // 检查路径遍历攻击
        if (containsPathTraversal(originalFilename)) {
            throw new BusinessException("无效的文件名");
        }

        // 获取文件扩展名
        String extension = getFileExtension(originalFilename);
        
        // 扩展名白名单校验
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new BusinessException("不支持的文件类型: " + extension);
        }

        // MIME类型校验
        String contentType = file.getContentType();
        if (contentType != null && !ALLOWED_MIME_TYPES.contains(contentType)) {
            log.warn("文件MIME类型不匹配: {}", contentType);
            // 双重校验失败，拒绝文件
            throw new BusinessException("文件类型验证失败");
        }

        // 文件大小校验
        long maxSize = parseFileSize(maxFileSize);
        if (file.getSize() > maxSize) {
            throw new BusinessException("文件大小超过限制");
        }
    }

    public void validateFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            throw new BusinessException("文件名不能为空");
        }
        
        if (containsPathTraversal(fileName)) {
            throw new BusinessException("无效的文件名");
        }

        String extension = getFileExtension(fileName);
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new BusinessException("不支持的文件类型: " + extension);
        }
    }

    private boolean containsPathTraversal(String filename) {
        return filename.contains("..") || 
               filename.contains("/") || 
               filename.contains("\\");
    }

    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDotIndex + 1);
    }

    private long parseFileSize(String size) {
        size = size.toUpperCase().trim();
        long multiplier = 1;
        
        if (size.endsWith("KB")) {
            multiplier = 1024;
            size = size.substring(0, size.length() - 2);
        } else if (size.endsWith("MB")) {
            multiplier = 1024 * 1024;
            size = size.substring(0, size.length() - 2);
        } else if (size.endsWith("GB")) {
            multiplier = 1024 * 1024 * 1024;
            size = size.substring(0, size.length() - 2);
        }
        
        return Long.parseLong(size.trim()) * multiplier;
    }
}
