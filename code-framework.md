# 项目代码框架设计

## 基于技术文档的完整代码结构

---

## 1. 项目目录结构

```
backend/
├── src/main/java/com/example/ragchat/
│   ├── controller/              # REST API控制层
│   ├── service/                 # 业务逻辑层
│   ├── serviceAi/               # AI Service层（Harness架构）
│   ├── repository/              # 数据访问层
│   ├── entity/                  # 数据库实体
│   ├── dto/                     # 数据传输对象
│   ├── config/                  # 配置类
│   ├── security/                # 安全组件
│   ├── exception/               # 异常处理
│   └── util/                    # 工具类
└── src/main/resources/
    ├── application.yml          # 应用配置
    └── schema.sql               # 数据库初始化
```

---

## 2. Controller层

### 2.1 AuthController

```java
package com.example.ragchat.controller;

import com.example.ragchat.dto.request.LoginRequest;
import com.example.ragchat.dto.request.RefreshRequest;
import com.example.ragchat.dto.response.LoginResponse;
import com.example.ragchat.dto.response.RefreshResponse;
import com.example.ragchat.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    
    private final UserService userService;
    
    public AuthController(UserService userService) {
        this.userService = userService;
    }
    
    /** 用户登录 */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        // 调用UserService验证用户并生成Token
        LoginResponse response = userService.login(request.getUsername(), request.getPassword());
        return ResponseEntity.ok(response);
    }
    
    /** 刷新Token */
    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(@RequestBody RefreshRequest request) {
        RefreshResponse response = userService.refreshToken(request.getRefreshToken());
        return ResponseEntity.ok(response);
    }
    
    /** 用户登出 */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String token) {
        userService.logout(token);
        return ResponseEntity.noContent().build();
    }
}
```

### 2.2 FileController

```java
package com.example.ragchat.controller;

import com.example.ragchat.dto.request.FileUploadRequest;
import com.example.ragchat.dto.response.FileListResponse;
import com.example.ragchat.dto.response.FileUploadResponse;
import com.example.ragchat.service.FileService;
import com.example.ragchat.service.FileValidationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {
    
    private final FileService fileService;
    private final FileValidationService fileValidationService;
    
    public FileController(FileService fileService, FileValidationService fileValidationService) {
        this.fileService = fileService;
        this.fileValidationService = fileValidationService;
    }
    
    /** 普通文件上传 */
    @PostMapping("/upload")
    public ResponseEntity<FileUploadResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "directoryId", required = false) String directoryId) {
        
        // P0级：文件类型校验
        fileValidationService.validateFile(file);
        
        FileUploadResponse response = fileService.uploadFile(file, directoryId);
        return ResponseEntity.ok(response);
    }
    
    /** 下载文件 */
    @GetMapping("/download/{fileId}")
    public ResponseEntity<byte[]> downloadFile(@PathVariable String fileId) {
        return fileService.downloadFile(fileId);
    }
    
    /** 删除文件 */
    @DeleteMapping("/{fileId}")
    public ResponseEntity<Void> deleteFile(@PathVariable String fileId) {
        fileService.deleteFile(fileId);
        return ResponseEntity.noContent().build();
    }
    
    /** 获取文件列表 */
    @GetMapping
    public ResponseEntity<FileListResponse> listFiles(
            @RequestParam(value = "directoryId", required = false) String directoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        FileListResponse response = fileService.listFiles(directoryId, page, size);
        return ResponseEntity.ok(response);
    }
}
```

### 2.3 MultipartUploadController

```java
package com.example.ragchat.controller;

import com.example.ragchat.dto.request.*;
import com.example.ragchat.dto.response.*;
import com.example.ragchat.service.MultipartUploadService;
import com.example.ragchat.service.FileValidationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/files/upload")
public class MultipartUploadController {
    
    private final MultipartUploadService uploadService;
    private final FileValidationService validationService;
    
    public MultipartUploadController(MultipartUploadService uploadService, 
                                     FileValidationService validationService) {
        this.uploadService = uploadService;
        this.validationService = validationService;
    }
    
    /** 初始化分片上传 */
    @PostMapping("/init")
    public ResponseEntity<InitUploadResponse> initUpload(@RequestBody InitUploadRequest request) {
        validationService.validateFileName(request.getFileName());
        String uploadId = uploadService.initMultipartUpload("files", request.getFileName());
        return ResponseEntity.ok(InitUploadResponse.of(uploadId));
    }
    
    /** 上传分片 */
    @PostMapping("/part")
    public ResponseEntity<UploadPartResponse> uploadPart(
            @RequestParam("uploadId") String uploadId,
            @RequestParam("partNumber") int partNumber,
            @RequestParam("file") MultipartFile file) {
        
        String etag = uploadService.uploadPart("files", uploadId, partNumber, 
                                               file.getInputStream(), file.getSize());
        return ResponseEntity.ok(UploadPartResponse.of(partNumber, etag));
    }
    
    /** 完成分片上传 */
    @PostMapping("/complete")
    public ResponseEntity<FileUploadResult> completeUpload(
            @RequestBody CompleteUploadRequest request) {
        
        uploadService.completeMultipartUpload("files", request.getUploadId(), request.getParts());
        return ResponseEntity.ok(FileUploadResult.success());
    }
    
    /** 取消分片上传 */
    @DeleteMapping("/abort")
    public ResponseEntity<Void> abortUpload(@RequestParam("uploadId") String uploadId) {
        uploadService.abortMultipartUpload("files", uploadId);
        return ResponseEntity.noContent().build();
    }
    
    /** 查询已上传分片 */
    @GetMapping("/parts")
    public ResponseEntity<List<PartInfo>> listParts(@RequestParam("uploadId") String uploadId) {
        return ResponseEntity.ok(uploadService.listParts("files", uploadId));
    }
}
```

### 2.4 SearchController

```java
package com.example.ragchat.controller;

import com.example.ragchat.dto.request.SemanticSearchRequest;
import com.example.ragchat.dto.response.SearchResponse;
import com.example.ragchat.serviceAi.SearchSkill;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {
    
    private final SearchSkill searchSkill;
    
    public SearchController(SearchSkill searchSkill) {
        this.searchSkill = searchSkill;
    }
    
    /** 关键词搜索 */
    @GetMapping
    public ResponseEntity<SearchResponse> keywordSearch(
            @RequestParam("query") String query,
            @RequestParam(defaultValue = "10") int limit) {
        
        SearchResponse response = searchSkill.keywordSearch(query, limit);
        return ResponseEntity.ok(response);
    }
    
    /** 语义搜索 */
    @PostMapping("/semantic")
    public ResponseEntity<SearchResponse> semanticSearch(@RequestBody SemanticSearchRequest request) {
        SearchResponse response = searchSkill.semanticSearch(request.getQuery(), 
                                                             request.getFileIds(), 
                                                             request.getLimit());
        return ResponseEntity.ok(response);
    }
}
```

### 2.5 ConversationController

```java
package com.example.ragchat.controller;

import com.example.ragchat.dto.request.MessageRequest;
import com.example.ragchat.dto.response.ConversationDetailResponse;
import com.example.ragchat.dto.response.ConversationListResponse;
import com.example.ragchat.dto.response.MessageResponse;
import com.example.ragchat.serviceAi.AISkill;
import com.example.ragchat.serviceAi.context.ContextManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {
    
    private final AISkill aiSkill;
    private final ContextManager contextManager;
    
    public ConversationController(AISkill aiSkill, ContextManager contextManager) {
        this.aiSkill = aiSkill;
        this.contextManager = contextManager;
    }
    
    /** 创建对话 */
    @PostMapping
    public ResponseEntity<ConversationDetailResponse> createConversation(
            @RequestParam(value = "fileId", required = false) String fileId) {
        
        ConversationDetailResponse response = aiSkill.createConversation(fileId);
        return ResponseEntity.ok(response);
    }
    
    /** 发送消息 */
    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<MessageResponse> sendMessage(
            @PathVariable String conversationId,
            @RequestBody MessageRequest request) {
        
        // P0级：通过断路器保护LLM调用
        MessageResponse response = aiSkill.sendMessage(conversationId, request.getContent());
        return ResponseEntity.ok(response);
    }
    
    /** 获取对话详情 */
    @GetMapping("/{conversationId}")
    public ResponseEntity<ConversationDetailResponse> getConversation(
            @PathVariable String conversationId) {
        
        ConversationDetailResponse response = contextManager.getConversation(conversationId);
        return ResponseEntity.ok(response);
    }
    
    /** 获取对话列表 */
    @GetMapping
    public ResponseEntity<ConversationListResponse> listConversations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        ConversationListResponse response = contextManager.listConversations(page, size);
        return ResponseEntity.ok(response);
    }
}
```

---

## 3. Service层

### 3.1 UserService

```java
package com.example.ragchat.service;

import com.example.ragchat.dto.response.LoginResponse;
import com.example.ragchat.dto.response.RefreshResponse;

public interface UserService {
    
    /** 用户登录 */
    LoginResponse login(String username, String password);
    
    /** 刷新Token */
    RefreshResponse refreshToken(String refreshToken);
    
    /** 用户登出 */
    void logout(String token);
}
```

### 3.2 FileService

```java
package com.example.ragchat.service;

import com.example.ragchat.dto.response.FileListResponse;
import com.example.ragchat.dto.response.FileUploadResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

public interface FileService {
    
    /** 上传文件 */
    FileUploadResponse uploadFile(MultipartFile file, String directoryId);
    
    /** 下载文件 */
    ResponseEntity<byte[]> downloadFile(String fileId);
    
    /** 删除文件 */
    void deleteFile(String fileId);
    
    /** 获取文件列表 */
    FileListResponse listFiles(String directoryId, int page, int size);
}
```

### 3.3 FileValidationService（P0级）

```java
package com.example.ragchat.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileValidationService {
    
    /** 校验文件（类型、大小、安全性） */
    void validateFile(MultipartFile file);
    
    /** 校验文件名（防止路径遍历） */
    void validateFileName(String fileName);
    
    /** 获取允许的文件类型列表 */
    java.util.List<String> getAllowedFileTypes();
}
```

### 3.4 MultipartUploadService（P1级）

```java
package com.example.ragchat.service;

import com.example.ragchat.dto.response.PartInfo;
import java.io.InputStream;
import java.util.List;

public interface MultipartUploadService {
    
    /** 初始化分片上传 */
    String initMultipartUpload(String bucket, String objectName);
    
    /** 上传分片 */
    String uploadPart(String bucket, String uploadId, int partNumber, 
                      InputStream inputStream, long size);
    
    /** 完成分片上传 */
    void completeMultipartUpload(String bucket, String uploadId, List<PartInfo> parts);
    
    /** 取消分片上传 */
    void abortMultipartUpload(String bucket, String uploadId);
    
    /** 查询已上传分片 */
    List<PartInfo> listParts(String bucket, String uploadId);
}
```

### 3.5 CacheManager（P1级）

```java
package com.example.ragchat.service;

import com.example.ragchat.dto.response.FileInfo;
import com.example.ragchat.dto.response.SearchResult;
import com.example.ragchat.serviceAi.context.UserPreferences;
import java.util.List;

public interface CacheManager {
    
    /** 获取用户偏好 */
    UserPreferences getUserPreferences(String userId);
    
    /** 保存用户偏好 */
    void saveUserPreferences(String userId, UserPreferences preferences);
    
    /** 获取文件元数据 */
    FileInfo getFileMetadata(String fileId);
    
    /** 缓存搜索结果 */
    void cacheSearchResult(String userId, String query, List<SearchResult> results);
    
    /** 获取缓存的搜索结果 */
    List<SearchResult> getCachedSearchResult(String userId, String query);
    
    /** 缓存向量嵌入 */
    void cacheEmbedding(String text, float[] embedding);
    
    /** 获取缓存的向量嵌入 */
    float[] getCachedEmbedding(String text);
    
    /** 清除用户相关缓存 */
    void invalidateUserCache(String userId);
    
    /** 清除文件相关缓存 */
    void invalidateFileCache(String fileId);
}
```

---

## 4. ServiceAI层（Harness架构核心）

### 4.1 RagChatAgent

```java
package com.example.ragchat.serviceAi.harness;

import com.example.ragchat.dto.response.AgentResponse;

public interface RagChatAgent {
    
    /** 处理用户请求 */
    AgentResponse processRequest(String userId, String input);
}
```

### 4.2 IntentRecognizer

```java
package com.example.ragchat.serviceAi.intent;

public interface IntentRecognizer {
    
    /** 识别用户意图 */
    Intent recognize(String input);
}

/** 意图枚举 */
public enum IntentType {
    FILE_UPLOAD,
    FILE_DOWNLOAD,
    FILE_DELETE,
    FILE_LIST,
    DIRECTORY_CREATE,
    KEYWORD_SEARCH,
    SEMANTIC_SEARCH,
    ADVANCED_SEARCH,
    FILE_QA,
    MULTI_FILE_QA,
    FILE_SUMMARIZE,
    ANALYZE_COMPARE,
    UNKNOWN
}

/** 意图对象 */
public class Intent {
    private IntentType type;
    private double confidence;
    private java.util.Map<String, Object> parameters;
    
    // Getters and Setters
}
```

### 4.3 SkillScheduler

```java
package com.example.ragchat.serviceAi.scheduler;

import com.example.ragchat.dto.response.AgentResponse;
import com.example.ragchat.serviceAi.intent.Intent;

public interface SkillScheduler {
    
    /** 根据意图路由到对应技能 */
    AgentResponse execute(Intent intent, String userId);
}
```

### 4.4 ContextManager

```java
package com.example.ragchat.serviceAi.context;

import com.example.ragchat.dto.response.ConversationDetailResponse;
import com.example.ragchat.dto.response.ConversationListResponse;

public interface ContextManager {
    
    /** 获取对话上下文 */
    ConversationContext getContext(String conversationId);
    
    /** 保存上下文 */
    void saveContext(ConversationContext context);
    
    /** 获取对话详情 */
    ConversationDetailResponse getConversation(String conversationId);
    
    /** 获取对话列表 */
    ConversationListResponse listConversations(int page, int size);
    
    /** 删除对话 */
    void deleteConversation(String conversationId);
}

/** 对话上下文 */
public class ConversationContext {
    private String conversationId;
    private String userId;
    private String fileId;
    private java.util.List<MessageRecord> history;
    private UserPreferences preferences;
    
    // Getters and Setters
}

/** 用户偏好 */
public class UserPreferences {
    private String userId;
    private String defaultModel;
    private int maxResponseLength;
    private boolean autoSummarize;
    // 其他偏好设置...
}
```

### 4.5 Skills层

```java
// FileSkill.java
package com.example.ragchat.serviceAi.skills;

import com.example.ragchat.dto.response.AgentResponse;

public interface FileSkill {
    
    /** 上传文件 */
    AgentResponse upload(String fileName, byte[] content);
    
    /** 下载文件 */
    AgentResponse download(String fileId);
    
    /** 删除文件 */
    AgentResponse delete(String fileId);
    
    /** 获取文件列表 */
    AgentResponse list(String directoryId);
    
    /** 创建目录 */
    AgentResponse createDirectory(String parentId, String name);
}

// SearchSkill.java
package com.example.ragchat.serviceAi.skills;

import com.example.ragchat.dto.response.AgentResponse;
import com.example.ragchat.dto.response.SearchResponse;

public interface SearchSkill {
    
    /** 关键词搜索 */
    SearchResponse keywordSearch(String query, int limit);
    
    /** 语义搜索 */
    SearchResponse semanticSearch(String query, java.util.List<String> fileIds, int limit);
    
    /** 高级搜索 */
    SearchResponse advancedSearch(java.util.Map<String, Object> criteria);
}

// AISkill.java
package com.example.ragchat.serviceAi.skills;

import com.example.ragchat.dto.response.AgentResponse;
import com.example.ragchat.dto.response.ConversationDetailResponse;
import com.example.ragchat.dto.response.MessageResponse;

public interface AISkill {
    
    /** 创建对话 */
    ConversationDetailResponse createConversation(String fileId);
    
    /** 发送消息 */
    MessageResponse sendMessage(String conversationId, String content);
    
    /** 文件问答 */
    MessageResponse fileQA(String fileId, String question);
    
    /** 多文件问答 */
    MessageResponse multiFileQA(java.util.List<String> fileIds, String question);
    
    /** 文件总结 */
    MessageResponse summarize(String fileId);
    
    /** 分析对比 */
    MessageResponse analyzeCompare(java.util.List<String> fileIds, String question);
}
```

---

## 5. MCP适配层

### 5.1 FileMCP

```java
package com.example.ragchat.serviceAi.mcp;

import java.io.InputStream;
import java.io.OutputStream;

public interface FileMCP {
    
    /** 上传文件到MinIO */
    String upload(String bucket, String objectName, InputStream inputStream, long size);
    
    /** 下载文件 */
    void download(String bucket, String objectName, OutputStream outputStream);
    
    /** 删除文件 */
    void delete(String bucket, String objectName);
    
    /** 检查文件是否存在 */
    boolean exists(String bucket, String objectName);
    
    /** 获取文件大小 */
    long getSize(String bucket, String objectName);
}
```

### 5.2 SearchMCP（P1级优化）

```java
package com.example.ragchat.serviceAi.mcp;

import java.util.List;

public interface SearchMCP {
    
    /** 向量搜索（带索引优化） */
    List<Long> searchVectors(String collectionName, float[] queryVector, int topK, String userId);
    
    /** 批量插入向量（优化写入性能） */
    void insertVectors(String collectionName, List<float[]> vectors, 
                       List<String> fileIds, String userId);
    
    /** 创建向量索引（IVF_FLAT） */
    void createIndex(String collectionName);
    
    /** 删除向量 */
    void deleteVectors(String collectionName, String fileId);
}
```

### 5.3 AIMCP（带熔断保护）

```java
package com.example.ragchat.serviceAi.mcp;

public interface AIMCP {
    
    /** 调用DeepSeek API生成回答 */
    String generate(String prompt, java.util.Map<String, Object> options);
    
    /** 生成向量嵌入 */
    float[] embed(String text);
    
    /** 获取模型状态 */
    boolean isHealthy();
}
```

---

## 6. Security层

### 6.1 JwtTokenProvider

```java
package com.example.ragchat.security;

public interface JwtTokenProvider {
    
    /** 生成Token */
    String generateToken(String userId, String role);
    
    /** 从Token中提取用户ID */
    String getUserIdFromToken(String token);
    
    /** 从Token中提取角色 */
    String getRoleFromToken(String token);
    
    /** 验证Token */
    boolean validateToken(String token);
    
    /** 生成Refresh Token */
    String generateRefreshToken(String userId);
}
```

### 6.2 JwtAuthenticationFilter

```java
package com.example.ragchat.security;

import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private final JwtTokenProvider tokenProvider;
    
    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }
    
    @Override
    protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,
                                    jakarta.servlet.http.HttpServletResponse response,
                                    jakarta.servlet.FilterChain filterChain) 
            throws jakarta.servlet.ServletException, java.io.IOException {
        // 解析Token并设置认证上下文
    }
}
```

### 6.3 RateLimitFilter（P0级）

```java
package com.example.ragchat.security;

import org.springframework.web.filter.OncePerRequestFilter;

public class RateLimitFilter extends OncePerRequestFilter {
    
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    private final int requestsPerMinute;
    
    public RateLimitFilter(org.springframework.data.redis.core.StringRedisTemplate redisTemplate,
                           int requestsPerMinute) {
        this.redisTemplate = redisTemplate;
        this.requestsPerMinute = requestsPerMinute;
    }
    
    @Override
    protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,
                                    jakarta.servlet.http.HttpServletResponse response,
                                    jakarta.servlet.FilterChain filterChain) 
            throws jakarta.servlet.ServletException, java.io.IOException {
        // 基于IP限流：每分钟最多requestsPerMinute次请求
        // 超过阈值封禁15分钟
    }
}
```

---

## 7. Config层

### 7.1 SecurityConfig

```java
package com.example.ragchat.config;

import com.example.ragchat.security.JwtAuthenticationFilter;
import com.example.ragchat.security.RateLimitFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    
    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          RateLimitFilter rateLimitFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitFilter = rateLimitFilter;
    }
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(
                    org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            // P0级：限流过滤器（放在认证过滤器之前）
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
}
```

### 7.2 CircuitBreakerConfig（P0级）

```java
package com.example.ragchat.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;

@Configuration
public class CircuitBreakerConfig {
    
    /** 断路器配置 */
    @Bean
    public CircuitBreaker circuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)      // 失败率超过50%开启熔断
                .waitDurationInOpenState(Duration.ofSeconds(60))  // 熔断60秒
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.TIME_BASED)
                .slidingWindowSize(100)
                .minimumNumberOfCalls(10)
                .build();
        return CircuitBreaker.of("llm-api", config);
    }
    
    /** 重试配置 */
    @Bean
    public Retry retry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofSeconds(1))
                .build();
        return Retry.of("llm-api", config);
    }
    
    /** 限流配置 */
    @Bean
    public RateLimiter rateLimiter() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(10)  // 每秒最多10次
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .build();
        return RateLimiter.of("llm-api", config);
    }
    
    /** 舱壁配置 */
    @Bean
    public Bulkhead bulkhead() {
        BulkheadConfig config = BulkheadConfig.custom()
                .maxConcurrentCalls(20)  // 最多20并发
                .build();
        return Bulkhead.of("llm-api", config);
    }
}
```

### 7.3 CacheConfig（P1级）

```java
package com.example.ragchat.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {
    
    /** Caffeine缓存构建器 */
    @Bean
    public Caffeine<Object, Object> caffeineCacheBuilder() {
        return Caffeine.newBuilder()
                .initialCapacity(500)
                .maximumSize(2000)
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .recordStats();
    }
    
    /** 本地缓存Bean */
    @Bean
    public Cache<String, Object> localCache(Caffeine<Object, Object> caffeine) {
        return caffeine.build();
    }
}
```

### 7.4 MilvusConfig（P1级索引优化）

```java
package com.example.ragchat.config;

import io.milvus.client.MilvusClient;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MilvusConfig {
    
    @Value("${milvus.host:localhost}")
    private String host;
    
    @Value("${milvus.port:19530}")
    private int port;
    
    @Bean
    public MilvusClient milvusClient() {
        ConnectParam connectParam = ConnectParam.newBuilder()
                .withHost(host)
                .withPort(port)
                .build();
        return new MilvusServiceClient(connectParam);
    }
}
```

---

## 8. Entity层

```java
// User.java
package com.example.ragchat.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(unique = true, nullable = false, length = 50)
    private String username;
    
    @Column(unique = true, nullable = false)
    private String email;
    
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    
    @Column(nullable = false, length = 20)
    private String role = "user";
    
    @Column(nullable = false, length = 20)
    private String status = "active";
    
    @Column(name = "failed_login_attempts")
    private Integer failedLoginAttempts = 0;
    
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
    
    // Getters and Setters
}

// File.java
package com.example.ragchat.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "files")
public class File {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false)
    private String filename;
    
    @Column(name = "original_filename", nullable = false)
    private String originalFilename;
    
    @Column(name = "file_size", nullable = false)
    private Long fileSize;
    
    @Column(name = "file_type", nullable = false, length = 100)
    private String fileType;
    
    @Column(name = "directory_id")
    private String directoryId;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "storage_key", unique = true)
    private String storageKey;
    
    @Column(nullable = false, length = 64)
    private String checksum;
    
    @Column(name = "current_version")
    private Integer currentVersion = 1;
    
    @Column(name = "is_deleted")
    private Boolean isDeleted = false;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    
    // Getters and Setters
}

// Conversation.java
package com.example.ragchat.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "conversations")
public class Conversation {
    @Id
    private String id;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "file_id")
    private String fileId;
    
    private String title;
    
    @Column(name = "is_archived")
    private Boolean isArchived = false;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;
    
    // Getters and Setters
}

// Message.java
package com.example.ragchat.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
public class Message {
    @Id
    private String id;
    
    @Column(name = "conversation_id", nullable = false)
    private String conversationId;
    
    @Column(nullable = false, length = 20)
    private String role;
    
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;
    
    @Column(columnDefinition = "JSON")
    private String metadata = "{}";
    
    @Column(columnDefinition = "JSON")
    private String sources = "[]";
    
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    
    // Getters and Setters
}

// AuditLog.java
package com.example.ragchat.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    private String id;
    
    @Column(name = "user_id")
    private String userId;
    
    @Column(nullable = false, length = 100)
    private String action;
    
    @Column(name = "resource_type", nullable = false, length = 50)
    private String resourceType;
    
    @Column(name = "resource_id")
    private String resourceId;
    
    @Column(name = "ip_address", length = 50)
    private String ipAddress;
    
    @Column(columnDefinition = "JSON")
    private String details = "{}";
    
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    
    // Getters and Setters
}
```

---

## 9. DTO层（核心请求/响应对象）

### 9.1 请求DTO

```java
// LoginRequest.java
public class LoginRequest {
    private String username;
    private String password;
    // Getters and Setters
}

// RefreshRequest.java
public class RefreshRequest {
    private String refreshToken;
    // Getters and Setters
}

// SemanticSearchRequest.java
public class SemanticSearchRequest {
    private String query;
    private java.util.List<String> fileIds;
    private Integer limit = 10;
    // Getters and Setters
}

// MessageRequest.java
public class MessageRequest {
    private String content;
    // Getters and Setters
}

// InitUploadRequest.java
public class InitUploadRequest {
    private String fileName;
    private Long fileSize;
    // Getters and Setters
}

// CompleteUploadRequest.java
public class CompleteUploadRequest {
    private String uploadId;
    private java.util.List<PartInfo> parts;
    // Getters and Setters
}
```

### 9.2 响应DTO

```java
// LoginResponse.java
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private Long expiresIn;
    private com.example.ragchat.dto.response.UserInfo user;
    
    public static LoginResponse of(String accessToken, String refreshToken, 
                                    Long expiresIn, UserInfo user) {
        LoginResponse response = new LoginResponse();
        response.accessToken = accessToken;
        response.refreshToken = refreshToken;
        response.expiresIn = expiresIn;
        response.user = user;
        return response;
    }
}

// RefreshResponse.java
public class RefreshResponse {
    private String accessToken;
    private Long expiresIn;
    // Factory methods
}

// FileUploadResponse.java
public class FileUploadResponse {
    private String fileId;
    private String filename;
    private Long fileSize;
    private String storageKey;
    // Factory methods
}

// SearchResponse.java
public class SearchResponse {
    private java.util.List<SearchResult> results;
    private Long total;
    // Factory methods
}

// MessageResponse.java
public class MessageResponse {
    private String id;
    private String conversationId;
    private String role;
    private String content;
    private java.util.List<SourceInfo> sources;
    private java.time.LocalDateTime createdAt;
    // Factory methods
}

// AgentResponse.java
public class AgentResponse {
    private String content;
    private com.example.ragchat.serviceAi.intent.IntentType intentType;
    private java.util.Map<String, Object> metadata;
    // Factory methods
}
```

---

## 10. 模块调用关系图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        Controller层                                    │
│  AuthController ──────► UserService                                   │
│  FileController ──────► FileService ──────► FileValidationService     │
│  MultipartUploadController ──────► MultipartUploadService             │
│  SearchController ──────► SearchSkill                                 │
│  ConversationController ──────► AISkill ──────► ContextManager       │
└───────────────────────────┬───────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        ServiceAI层                                     │
│  RagChatAgent ──────► IntentRecognizer                                │
│               ──────► SkillScheduler                                   │
│                      ├─────► FileSkill                                │
│                      ├─────► SearchSkill ──────► SearchMCP            │
│                      └─────► AISkill ──────► AIMCP (带熔断)           │
│  ContextManager ──────► CacheManager                                  │
└───────────────────────────┬───────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        MCP层                                          │
│  FileMCP ──────► MinIO                                                │
│  SearchMCP ──────► Milvus (P1索引优化)                                │
│  AIMCP ──────► DeepSeek API (带断路器)                                │
└───────────────────────────┬───────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        数据层                                          │
│  PostgreSQL ──────► 用户、文件、对话、消息、审计日志                     │
│  Milvus ──────► 向量数据                                               │
│  MinIO ──────► 文件存储                                                │
│  Redis ──────► 缓存、限流计数、会话                                     │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 11. P0/P1优化项实现位置

| 优先级 | 优化项 | 实现位置 |
|--------|--------|----------|
| P0 | API限流 | `security/RateLimitFilter.java` |
| P0 | 断路器模式 | `config/CircuitBreakerConfig.java` + `serviceAi/mcp/AIMCP.java` |
| P0 | 文件类型校验 | `service/FileValidationService.java` |
| P1 | 向量索引调优 | `serviceAi/mcp/SearchMCP.java` + `config/MilvusConfig.java` |
| P1 | 分片上传 | `service/MultipartUploadService.java` + `controller/MultipartUploadController.java` |
| P1 | 缓存策略 | `service/CacheManager.java` + `config/CacheConfig.java` |

---

## 12. 关键技术要点

### 12.1 安全性

- **认证**: JWT无状态认证，Access Token有效期2小时，Refresh Token有效期24小时
- **权限**: RBAC模型，用户数据自动隔离
- **限流**: 基于IP，每分钟60次请求，超阈值封禁15分钟（Redis实现）
- **文件安全**: 白名单校验，防止路径遍历攻击

### 12.2 稳定性

- **熔断保护**: LLM调用使用Resilience4j断路器，失败率>50%熔断60秒
- **重试机制**: 最多重试3次，间隔1秒
- **并发控制**: 最多20个并发LLM调用，每秒最多10次

### 12.3 性能优化

- **向量索引**: IVF_FLAT索引，nlist=1024，nprobe=10
- **批量插入**: 每批100条，异步刷新
- **多级缓存**: Caffeine本地缓存 + Redis分布式缓存
- **分片上传**: 50MB/片，支持断点续传

### 12.4 Harness架构

- **意图识别**: 支持13种意图类型
- **技能调度**: 根据意图路由到对应技能
- **上下文管理**: 会话状态、用户偏好管理
- **MCP适配**: 封装外部服务调用，便于扩展
