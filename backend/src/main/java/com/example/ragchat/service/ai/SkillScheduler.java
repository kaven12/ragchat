package com.example.ragchat.service.ai;

import com.example.ragchat.mcp.AIMCP;
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

    public SkillResult executeSkill(Intent intent, Map<String, Object> entities,
                                    String userId, String conversationId) {
        long startTime = System.currentTimeMillis();
        log.info("[SkillScheduler.executeSkill] 方法开始 | intent={}, userId={}, conversationId={}",
                intent, userId, conversationId);
        log.debug("[SkillScheduler.executeSkill] 实体参数 | entities={}", entities);

        try {
            SkillResult result = switch (intent) {
                case DOCUMENT_QA -> executeDocumentQASkill(entities, userId, conversationId);
                case FILE_SEARCH -> executeFileSearchSkill(entities, userId, conversationId);
                case CONVERSATION -> executeConversationSkill(entities, conversationId);
                case FILE_UPLOAD, FILE_DELETE -> {
                    log.warn("[SkillScheduler.executeSkill] 不支持的技能类型 | intent={}", intent);
                    yield SkillResult.failure("该操作需要通过API接口执行");
                }
                case UNKNOWN -> {
                    log.info("[SkillScheduler.executeSkill] 未知意图，尝试使用对话技能");
                    yield executeConversationSkill(entities, conversationId);
                }
            };

            long costTime = System.currentTimeMillis() - startTime;
            log.info("[SkillScheduler.executeSkill] 技能执行完成 | intent={}, success={}, costTime={}ms",
                    intent, result.isSuccess(), costTime);

            return result;

        } catch (Exception e) {
            log.error("[SkillScheduler.executeSkill] 技能执行异常 | intent={}, error={}, stackTrace={}",
                    intent, e.getMessage(), e.getStackTrace());
            return SkillResult.failure("技能执行失败: " + e.getMessage());
        }
    }

    private SkillResult executeDocumentQASkill(Map<String, Object> entities, String userId, String conversationId) {
        long startTime = System.currentTimeMillis();
        log.info("[SkillScheduler.executeDocumentQASkill] 开始文档问答 | userId={}, conversationId={}",
                userId, conversationId);

        try {
            String query = (String) entities.getOrDefault("query", "");
            @SuppressWarnings("unchecked")
            List<String> fileIds = (List<String>) entities.getOrDefault("fileIds", null);

            log.debug("[SkillScheduler.executeDocumentQASkill] 查询参数 | query={}, queryLength={}, fileIds={}",
                    query, query.length(), fileIds);

            // 1. 语义搜索获取相关文档片段
            log.debug("[SkillScheduler.executeDocumentQASkill] 开始语义搜索 | query={}", query);
            long searchStartTime = System.currentTimeMillis();

            float[] queryEmbedding = generateEmbedding(query);
            List<SearchMCP.SearchResult> searchResults = searchMCP.search(queryEmbedding, 5, fileIds);

            long searchCostTime = System.currentTimeMillis() - searchStartTime;
            log.info("[SkillScheduler.executeDocumentQASkill] 语义搜索完成 | resultsCount={}, searchCostTime={}ms",
                    searchResults.size(), searchCostTime);

            if (searchResults.isEmpty()) {
                log.info("[SkillScheduler.executeDocumentQASkill] 未找到相关文档 | query={}", query);
                return SkillResult.success("没有找到相关文档内容", null);
            }

            // 2. 构建上下文
            log.debug("[SkillScheduler.executeDocumentQASkill] 构建搜索结果上下文 | resultsCount={}", searchResults.size());
            StringBuilder context = new StringBuilder();
            for (SearchMCP.SearchResult result : searchResults) {
                context.append("文档片段:\n");
                context.append(result.getChunkText());
                context.append("\n\n");
            }
            log.debug("[SkillScheduler.executeDocumentQASkill] 上下文构建完成 | contextLength={}", context.length());

            // 3. 调用LLM生成回答
            log.debug("[SkillScheduler.executeDocumentQASkill] 调用LLM生成回答 | contextLength={}, query={}",
                    context.length(), query);
            long llmStartTime = System.currentTimeMillis();

            String answer = aiMCP.generateWithContext(context.toString(), query);

            long llmCostTime = System.currentTimeMillis() - llmStartTime;
            log.info("[SkillScheduler.executeDocumentQASkill] LLM回答生成完成 | answerLength={}, llmCostTime={}ms",
                    answer.length(), llmCostTime);

            // 4. 提取来源信息
            List<Map<String, Object>> sources = searchResults.stream()
                    .map(r -> Map.<String, Object>of(
                            "fileId", r.getFileId(),
                            "chunkText", r.getChunkText(),
                            "score", r.getScore()
                    ))
                    .toList();
            log.debug("[SkillScheduler.executeDocumentQASkill] 来源信息提取完成 | sourcesCount={}", sources.size());

            long costTime = System.currentTimeMillis() - startTime;
            log.info("[SkillScheduler.executeDocumentQASkill] 文档问答完成 | totalCostTime={}ms, answerLength={}, sourcesCount={}",
                    costTime, answer.length(), sources.size());

            return SkillResult.success(answer, sources);

        } catch (Exception e) {
            log.error("[SkillScheduler.executeDocumentQASkill] 文档问答异常 | error={}, stackTrace={}",
                    e.getMessage(), e.getStackTrace());
            return SkillResult.failure("文档问答失败: " + e.getMessage());
        }
    }

    private SkillResult executeFileSearchSkill(Map<String, Object> entities, String userId, String conversationId) {
        long startTime = System.currentTimeMillis();
        log.info("[SkillScheduler.executeFileSearchSkill] 开始文件搜索 | userId={}, conversationId={}",
                userId, conversationId);

        try {
            String query = (String) entities.getOrDefault("query", "");
            @SuppressWarnings("unchecked")
            List<String> fileIds = (List<String>) entities.getOrDefault("fileIds", null);

            log.debug("[SkillScheduler.executeFileSearchSkill] 搜索参数 | query={}, fileIds={}", query, fileIds);

            float[] queryEmbedding = generateEmbedding(query);
            log.debug("[SkillScheduler.executeFileSearchSkill] 查询向量生成完成 | dimension={}", queryEmbedding.length);

            List<SearchMCP.SearchResult> searchResults = searchMCP.search(queryEmbedding, 10, fileIds);
            log.info("[SkillScheduler.executeFileSearchSkill] 搜索完成 | resultsCount={}", searchResults.size());

            List<Map<String, Object>> sources = searchResults.stream()
                    .map(r -> Map.<String, Object>of(
                            "fileId", r.getFileId(),
                            "chunkText", r.getChunkText(),
                            "score", r.getScore()
                    ))
                    .toList();

            long costTime = System.currentTimeMillis() - startTime;
            log.info("[SkillScheduler.executeFileSearchSkill] 文件搜索完成 | costTime={}ms, resultsCount={}",
                    costTime, searchResults.size());

            return SkillResult.success(
                    "找到 " + searchResults.size() + " 条相关结果",
                    sources
            );

        } catch (Exception e) {
            log.error("[SkillScheduler.executeFileSearchSkill] 文件搜索异常 | error={}, stackTrace={}",
                    e.getMessage(), e.getStackTrace());
            return SkillResult.failure("文件搜索失败: " + e.getMessage());
        }
    }

    private SkillResult executeConversationSkill(Map<String, Object> entities, String conversationId) {
        long startTime = System.currentTimeMillis();
        log.info("[SkillScheduler.executeConversationSkill] 开始对话 | conversationId={}", conversationId);

        try {
            String query = (String) entities.getOrDefault("query", "");
            log.debug("[SkillScheduler.executeConversationSkill] 对话参数 | query={}", query);

            long llmStartTime = System.currentTimeMillis();
            String response = aiMCP.generate(query);
            long llmCostTime = System.currentTimeMillis() - llmStartTime;

            log.info("[SkillScheduler.executeConversationSkill] 对话完成 | responseLength={}, llmCostTime={}ms",
                    response.length(), llmCostTime);

            long costTime = System.currentTimeMillis() - startTime;
            log.debug("[SkillScheduler.executeConversationSkill] 方法执行完成 | totalCostTime={}ms", costTime);

            return SkillResult.success(response, null);

        } catch (Exception e) {
            log.error("[SkillScheduler.executeConversationSkill] 对话异常 | error={}, stackTrace={}",
                    e.getMessage(), e.getStackTrace());
            return SkillResult.failure("对话失败: " + e.getMessage());
        }
    }

    private float[] generateEmbedding(String text) {
        log.debug("[SkillScheduler.generateEmbedding] 生成查询向量 | textLength={}", text.length());
        float[] embedding = new float[1024];
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = (float) (Math.random() * 2 - 1);
        }
        log.debug("[SkillScheduler.generateEmbedding] 查询向量生成完成 | dimension={}", embedding.length);
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
