package com.example.ragchat.service;

import com.example.ragchat.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

@Slf4j
@Service
public class FileValidationService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md"
    );

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
        log.debug("[FileValidationService.validateFile] 开始验证文件 | fileName={}, size={}",
                file != null ? file.getOriginalFilename() : "null",
                file != null ? file.getSize() : 0);

        if (file == null || file.isEmpty()) {
            log.error("[FileValidationService.validateFile] 文件为空 | file={}", file);
            throw new BusinessException("文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            log.error("[FileValidationService.validateFile] 文件名为空");
            throw new BusinessException("文件名不能为空");
        }

        log.debug("[FileValidationService.validateFile] 原始文件名 | fileName={}", originalFilename);

        // 检查路径遍历攻击
        if (containsPathTraversal(originalFilename)) {
            log.error("[FileValidationService.validateFile] 检测到路径遍历攻击 | fileName={}", originalFilename);
            throw new BusinessException("无效的文件名");
        }

        // 获取文件扩展名
        String extension = getFileExtension(originalFilename);
        log.debug("[FileValidationService.validateFile] 文件扩展名 | extension={}", extension);

        // 扩展名白名单校验
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            log.error("[FileValidationService.validateFile] 不支持的扩展名 | extension={}, allowedExtensions={}",
                    extension, ALLOWED_EXTENSIONS);
            throw new BusinessException("不支持的文件类型: " + extension);
        }
        log.debug("[FileValidationService.validateFile] 扩展名校验通过");

        // MIME类型校验
        String contentType = file.getContentType();
        log.debug("[FileValidationService.validateFile] 文件MIME类型 | contentType={}", contentType);

        if (contentType != null && !ALLOWED_MIME_TYPES.contains(contentType)) {
            log.error("[FileValidationService.validateFile] MIME类型不匹配 | contentType={}, allowedMimeTypes={}",
                    contentType, ALLOWED_MIME_TYPES);
            throw new BusinessException("文件类型验证失败");
        }
        log.debug("[FileValidationService.validateFile] MIME类型校验通过");

        // 文件大小校验
        long maxSize = parseFileSize(maxFileSize);
        log.debug("[FileValidationService.validateFile] 文件大小校验 | actualSize={}, maxSize={}, maxSizeStr={}",
                file.getSize(), maxSize, maxFileSize);

        if (file.getSize() > maxSize) {
            log.error("[FileValidationService.validateFile] 文件大小超过限制 | actualSize={}, maxSize={}",
                    file.getSize(), maxSize);
            throw new BusinessException("文件大小超过限制");
        }

        log.info("[FileValidationService.validateFile] 文件验证通过 | fileName={}, size={}, extension={}",
                originalFilename, file.getSize(), extension);
    }

    public void validateFileName(String fileName) {
        log.debug("[FileValidationService.validateFileName] 开始验证文件名 | fileName={}", fileName);

        if (fileName == null || fileName.isEmpty()) {
            log.error("[FileValidationService.validateFileName] 文件名为空");
            throw new BusinessException("文件名不能为空");
        }

        if (containsPathTraversal(fileName)) {
            log.error("[FileValidationService.validateFileName] 检测到路径遍历攻击 | fileName={}", fileName);
            throw new BusinessException("无效的文件名");
        }

        String extension = getFileExtension(fileName);
        log.debug("[FileValidationService.validateFileName] 文件扩展名 | extension={}", extension);

        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            log.error("[FileValidationService.validateFileName] 不支持的扩展名 | extension={}", extension);
            throw new BusinessException("不支持的文件类型: " + extension);
        }

        log.info("[FileValidationService.validateFileName] 文件名验证通过 | fileName={}", fileName);
    }

    private boolean containsPathTraversal(String filename) {
        boolean contains = filename.contains("..") ||
               filename.contains("/") ||
               filename.contains("\\");

        if (contains) {
            log.warn("[FileValidationService.containsPathTraversal] 检测到路径遍历风险 | filename={}", filename);
        }

        return contains;
    }

    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            log.debug("[FileValidationService.getFileExtension] 无法获取扩展名 | filename={}", filename);
            return "";
        }
        String extension = filename.substring(lastDotIndex + 1);
        log.debug("[FileValidationService.getFileExtension] 扩展名提取结果 | filename={}, extension={}", filename, extension);
        return extension;
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

        long result = Long.parseLong(size.trim()) * multiplier;
        log.debug("[FileValidationService.parseFileSize] 文件大小解析结果 | sizeStr={}, result={}", size, result);
        return result;
    }
}
