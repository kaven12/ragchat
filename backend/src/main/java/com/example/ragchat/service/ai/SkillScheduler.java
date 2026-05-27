package com.example.ragchat.service.ai;

import com.example.ragchat.mcp.AIMCP;
import com.example.ragchat.mcp.FileMCP;
import com.example.ragchat.mcp.SearchMCP;
import com.example.ragchat.service.ai.IntentRecognizer.Intent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SkillScheduler {

    private final AIMCP aiMCP;
    private final SearchMCP searchMCP;
    private final FileMCP fileMCP;

    public SkillResult executeSkill(Intent intent, Map<String, Object> entities, 
                                    String userId, String conversationId) {
        log.info("Executing skill: {} with entities: {}", intent, entities);
        
        return switch (intent) {
            case DOCUMENT_QA -> executeDocumentQASkill(entities, userId);
            case FILE_SEARCH -> executeFileSearchSkill(entities, userId);
            case CONVERSATION -> executeConversationSkill(entities);
            case FILE_UPLOAD, FILE_DELETE -> 
                    SkillResult.failure("该操作需要通过API接口执行");
            case UNKNOWN -> executeConversationSkill(entities);
        };
    }

    private SkillResult executeDocumentQASkill(Map<String, Object> entities, String userId) {
        String query = (String) entities.getOrDefault("query", "");
        List<String> fileIds = (List<String>) entities.getOrDefault("fileIds", null);

        try {
            // 1. 语义搜索获取相关文档片段
            float[] queryEmbedding = generateEmbedding(query);
            List<SearchMCP.SearchResult> searchResults = searchMCP.search(queryEmbedding, 5, fileIds);

            if (searchResults.isEmpty()) {
                return SkillResult.success("没有找到相关文档内容", null);
            }

            // 2. 构建上下文
            StringBuilder context = new StringBuilder();
            for (SearchMCP.SearchResult result : searchResults) {
                context.append("文档片段:\n");
                context.append(result.getChunkText());
                context.append("\n\n");
            }

            // 3. 调用LLM生成回答
            String answer = aiMCP.generateWithContext(context.toString(), query);

            // 4. 提取来源信息
            List<Map<String, Object>> sources = searchResults.stream()
                    .map(r -> Map.<String, Object>of(
                            "fileId", r.getFileId(),
                            "chunkText", r.getChunkText(),
                            "score", r.getScore()
                    ))
                    .toList();

            return SkillResult.success(answer, sources);
        } catch (Exception e) {
            log.error("Document QA skill failed", e);
            return SkillResult.failure("文档问答失败: " + e.getMessage());
        }
    }

    private SkillResult executeFileSearchSkill(Map<String, Object> entities, String userId) {
        String query = (String) entities.getOrDefault("query", "");
        List<String> fileIds = (List<String>) entities.getOrDefault("fileIds", null);

        try {
            float[] queryEmbedding = generateEmbedding(query);
            List<SearchMCP.SearchResult> searchResults = searchMCP.search(queryEmbedding, 10, fileIds);

            return SkillResult.success(
                    "找到 " + searchResults.size() + " 条相关结果",
                    searchResults.stream()
                            .map(r -> Map.<String, Object>of(
                                    "fileId", r.getFileId(),
                                    "chunkText", r.getChunkText(),
                                    "score", r.getScore()
                            ))
                            .toList()
            );
        } catch (Exception e) {
            log.error("File search skill failed", e);
            return SkillResult.failure("文件搜索失败: " + e.getMessage());
        }
    }

    private SkillResult executeConversationSkill(Map<String, Object> entities) {
        String query = (String) entities.getOrDefault("query", "");
        
        try {
            String response = aiMCP.generate(query);
            return SkillResult.success(response, null);
        } catch (Exception e) {
            log.error("Conversation skill failed", e);
            return SkillResult.failure("对话失败: " + e.getMessage());
        }
    }

    private float[] generateEmbedding(String text) {
        // 实际实现应该调用嵌入模型生成向量
        // 这里返回一个模拟的1024维向量
        float[] embedding = new float[1024];
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = (float) (Math.random() * 2 - 1);
        }
        return embedding;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SkillResult {
        private boolean success;
        private String content;
        private List<Map<String, Object>> sources;

        public static SkillResult success(String content, List<Map<String, Object>> sources) {
            return SkillResult.builder()
                    .success(true)
                    .content(content)
                    .sources(sources)
                    .build();
        }

        public static SkillResult failure(String message) {
            return SkillResult.builder()
                    .success(false)
                    .content(message)
                    .build();
        }
    }
}
