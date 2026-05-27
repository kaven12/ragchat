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
        DOCUMENT_QA,
        FILE_SEARCH,
        FILE_UPLOAD,
        FILE_DELETE,
        CONVERSATION,
        UNKNOWN
    }

    public IntentResult recognize(String userQuery) {
        long startTime = System.currentTimeMillis();
        log.info("[IntentRecognizer.recognize] 方法开始 | queryLength={}, query前50字符={}",
                userQuery != null ? userQuery.length() : 0,
                userQuery != null && userQuery.length() > 50 ? userQuery.substring(0, 50) + "..." : userQuery);

        try {
            String prompt = buildIntentPrompt(userQuery);
            log.debug("[IntentRecognizer.recognize] 意图识别Prompt已构建 | prompt长度={}", prompt.length());

            String response = aiMCP.generate(prompt);
            log.debug("[IntentRecognizer.recognize] LLM响应已接收 | response长度={}, response前100字符={}",
                    response.length(),
                    response.length() > 100 ? response.substring(0, 100) + "..." : response);

            IntentResult result = parseIntentResponse(response);

            long costTime = System.currentTimeMillis() - startTime;
            log.info("[IntentRecognizer.recognize] 意图识别完成 | intent={}, confidence={}, costTime={}ms",
                    result.getIntent(), result.getConfidence(), costTime);

            return result;

        } catch (Exception e) {
            log.error("[IntentRecognizer.recognize] 意图识别异常 | error={}, stackTrace={}",
                    e.getMessage(), e.getStackTrace());
            log.info("[IntentRecognizer.recognize] 使用降级意图识别策略");
            IntentResult fallbackResult = fallbackIntent(userQuery);
            log.info("[IntentRecognizer.recognize] 降级意图识别结果 | intent={}, confidence={}",
                    fallbackResult.getIntent(), fallbackResult.getConfidence());
            return fallbackResult;
        }
    }

    private String buildIntentPrompt(String userQuery) {
        String prompt = """
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

        log.debug("[IntentRecognizer.buildIntentPrompt] Prompt构建完成 | prompt长度={}", prompt.length());
        return prompt;
    }

    private IntentResult parseIntentResponse(String response) {
        try {
            log.debug("[IntentRecognizer.parseIntentResponse] 开始解析LLM响应 | response={}", response);
            Map<String, Object> result = objectMapper.readValue(response, Map.class);
            String intentStr = (String) result.get("intent");
            double confidence = result.get("confidence") != null ?
                    ((Number) result.get("confidence")).doubleValue() : 0.5;
            @SuppressWarnings("unchecked")
            Map<String, Object> entities = (Map<String, Object>) result.getOrDefault("entities", new HashMap<>());

            Intent intent;
            try {
                intent = Intent.valueOf(intentStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("[IntentRecognizer.parseIntentResponse] 无效的意图类型 | intentStr={}", intentStr);
                intent = Intent.UNKNOWN;
            }

            log.debug("[IntentRecognizer.parseIntentResponse] 解析成功 | intent={}, confidence={}, entitiesCount={}",
                    intent, confidence, entities.size());

            return IntentResult.builder()
                    .intent(intent)
                    .confidence(confidence)
                    .entities(entities)
                    .build();

        } catch (Exception e) {
            log.error("[IntentRecognizer.parseIntentResponse] 响应解析失败 | error={}", e.getMessage());
            throw new RuntimeException("Failed to parse intent response", e);
        }
    }

    private IntentResult fallbackIntent(String userQuery) {
        log.info("[IntentRecognizer.fallbackIntent] 使用关键词匹配进行意图识别 | query={}", userQuery);
        String lowerQuery = userQuery.toLowerCase();

        Intent intent;
        Map<String, Object> entities = new HashMap<>();
        entities.put("query", userQuery);

        if (lowerQuery.contains("问") || lowerQuery.contains("答") ||
            lowerQuery.contains("什么") || lowerQuery.contains("如何") ||
            lowerQuery.contains("怎么") || lowerQuery.contains("为什么")) {
            intent = Intent.DOCUMENT_QA;
            log.debug("[IntentRecognizer.fallbackIntent] 识别为文档问答意图 | 关键词匹配");
        } else if (lowerQuery.contains("搜索") || lowerQuery.contains("查找") ||
                   lowerQuery.contains("找") || lowerQuery.contains("内容")) {
            intent = Intent.FILE_SEARCH;
            log.debug("[IntentRecognizer.fallbackIntent] 识别为文件搜索意图 | 关键词匹配");
        } else if (lowerQuery.contains("上传") || lowerQuery.contains("文件")) {
            intent = Intent.FILE_UPLOAD;
            log.debug("[IntentRecognizer.fallbackIntent] 识别为文件上传意图 | 关键词匹配");
        } else {
            intent = Intent.CONVERSATION;
            log.debug("[IntentRecognizer.fallbackIntent] 识别为日常对话意图 | 关键词匹配");
        }

        return IntentResult.builder()
                .intent(intent)
                .confidence(0.7)
                .entities(entities)
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
