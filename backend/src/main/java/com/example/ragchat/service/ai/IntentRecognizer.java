package com.example.ragchat.service.ai;

import com.example.ragchat.mcp.AIMCP;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class IntentRecognizer {

    private final AIMCP aiMCP;
    private final ObjectMapper objectMapper;

    public enum Intent {
        DOCUMENT_QA,        // 文档问答
        FILE_SEARCH,        // 文件搜索
        FILE_UPLOAD,        // 文件上传
        FILE_DELETE,        // 文件删除
        CONVERSATION,       // 对话管理
        UNKNOWN             // 未知意图
    }

    public IntentResult recognize(String userQuery) {
        String prompt = buildIntentPrompt(userQuery);
        
        try {
            String response = aiMCP.generate(prompt);
            return parseIntentResponse(response);
        } catch (Exception e) {
            log.warn("Intent recognition failed, using fallback: {}", e.getMessage());
            return fallbackIntent(userQuery);
        }
    }

    private String buildIntentPrompt(String userQuery) {
        return """
                请分析以下用户输入的意图，并返回JSON格式的结果：
                
                用户输入：%s
                
                可能的意图包括：
                - DOCUMENT_QA: 用户询问与上传文件相关的问题，需要读取文件内容进行回答
                - FILE_SEARCH: 用户想要搜索文件或文件内容
                - FILE_UPLOAD: 用户想要上传文件
                - FILE_DELETE: 用户想要删除文件
                - CONVERSATION: 用户进行日常对话或闲聊
                - UNKNOWN: 无法确定意图
                
                请返回严格的JSON格式：{"intent": "意图名称", "confidence": 置信度(0-1), "entities": {相关实体}}
                """.formatted(userQuery);
    }

    private IntentResult parseIntentResponse(String response) {
        try {
            Map<String, Object> result = objectMapper.readValue(response, Map.class);
            String intentStr = (String) result.get("intent");
            double confidence = result.get("confidence") != null ? 
                    ((Number) result.get("confidence")).doubleValue() : 0.5;
            @SuppressWarnings("unchecked")
            Map<String, Object> entities = (Map<String, Object>) result.getOrDefault("entities", new HashMap<>());

            Intent intent = Intent.valueOf(intentStr.toUpperCase());
            return IntentResult.builder()
                    .intent(intent)
                    .confidence(confidence)
                    .entities(entities)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse intent response: {}", e.getMessage());
            return fallbackIntent(response);
        }
    }

    private IntentResult fallbackIntent(String userQuery) {
        // 简单的基于关键词的意图识别作为降级方案
        String lowerQuery = userQuery.toLowerCase();
        
        if (lowerQuery.contains("问") || lowerQuery.contains("答") || 
            lowerQuery.contains("什么") || lowerQuery.contains("如何")) {
            return IntentResult.builder()
                    .intent(Intent.DOCUMENT_QA)
                    .confidence(0.7)
                    .entities(Map.of("query", userQuery))
                    .build();
        }
        
        if (lowerQuery.contains("搜索") || lowerQuery.contains("查找") || 
            lowerQuery.contains("找") || lowerQuery.contains("内容")) {
            return IntentResult.builder()
                    .intent(Intent.FILE_SEARCH)
                    .confidence(0.7)
                    .entities(Map.of("query", userQuery))
                    .build();
        }
        
        if (lowerQuery.contains("上传") || lowerQuery.contains("文件")) {
            return IntentResult.builder()
                    .intent(Intent.FILE_UPLOAD)
                    .confidence(0.6)
                    .entities(new HashMap<>())
                    .build();
        }
        
        return IntentResult.builder()
                .intent(Intent.CONVERSATION)
                .confidence(0.5)
                .entities(Map.of("query", userQuery))
                .build();
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class IntentResult {
        private Intent intent;
        private double confidence;
        private Map<String, Object> entities;
    }
}
