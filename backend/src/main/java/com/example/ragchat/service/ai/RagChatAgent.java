package com.example.ragchat.service.ai;

import com.example.ragchat.dto.response.MessageResponse;
import com.example.ragchat.entity.Conversation;
import com.example.ragchat.service.ai.IntentRecognizer.Intent;
import com.example.ragchat.service.ai.IntentRecognizer.IntentResult;
import com.example.ragchat.service.ai.SkillScheduler.SkillResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagChatAgent {

    private final IntentRecognizer intentRecognizer;
    private final SkillScheduler skillScheduler;
    private final ContextManager contextManager;

    public MessageResponse chat(String userId, String conversationId, String message) {
        long startTime = System.currentTimeMillis();
        log.info("[RagChatAgent.chat] 方法开始 | userId={}, conversationId={}, messageLength={}",
                userId, conversationId, message != null ? message.length() : 0);

        try {
            // 如果没有对话ID，创建新对话
            if (conversationId == null || conversationId.isEmpty()) {
                log.debug("[RagChatAgent.chat] 创建新对话 | userId={}", userId);
                Conversation conversation = contextManager.createConversation(userId, null);
                conversationId = conversation.getId();
                log.info("[RagChatAgent.chat] 新对话已创建 | conversationId={}", conversationId);
            }

            // 添加用户消息到上下文
            log.debug("[RagChatAgent.chat] 添加用户消息到上下文 | conversationId={}", conversationId);
            contextManager.addMessage(conversationId, "user", message, null);

            // 1. 意图识别
            log.debug("[RagChatAgent.chat] 开始意图识别 | message前50字符={}",
                    message.length() > 50 ? message.substring(0, 50) + "..." : message);
            IntentResult intentResult = intentRecognizer.recognize(message);
            log.info("[RagChatAgent.chat] 意图识别完成 | conversationId={}, intent={}, confidence={}",
                    conversationId, intentResult.getIntent(), intentResult.getConfidence());

            // 2. 技能调度执行
            log.debug("[RagChatAgent.chat] 开始技能调度 | conversationId={}, intent={}",
                    conversationId, intentResult.getIntent());
            long skillStartTime = System.currentTimeMillis();

            SkillResult skillResult = skillScheduler.executeSkill(
                    intentResult.getIntent(),
                    intentResult.getEntities(),
                    userId,
                    conversationId
            );

            long skillCostTime = System.currentTimeMillis() - skillStartTime;
            log.info("[RagChatAgent.chat] 技能执行完成 | conversationId={}, success={}, skillCostTime={}ms",
                    conversationId, skillResult.isSuccess(), skillCostTime);

            // 3. 添加AI响应到上下文
            List<Map<String, Object>> sources = skillResult.getSources();
            log.debug("[RagChatAgent.chat] 添加AI响应到上下文 | conversationId={}, sourcesCount={}",
                    conversationId, sources != null ? sources.size() : 0);

            var aiMessage = contextManager.addMessage(conversationId, "assistant",
                    skillResult.getContent(), sources);

            // 4. 构建响应
            List<MessageResponse.SourceInfo> sourceInfos = null;
            if (sources != null && !sources.isEmpty()) {
                sourceInfos = sources.stream()
                        .map(s -> MessageResponse.SourceInfo.builder()
                                .fileId((String) s.get("fileId"))
                                .filename((String) s.getOrDefault("filename", ""))
                                .chunkText((String) s.get("chunkText"))
                                .build())
                        .toList();
                log.debug("[RagChatAgent.chat] 来源信息已构建 | conversationId={}, sourceInfosCount={}",
                        conversationId, sourceInfos.size());
            }

            MessageResponse response = MessageResponse.builder()
                    .id(aiMessage.getId())
                    .conversationId(conversationId)
                    .role(aiMessage.getRole())
                    .content(aiMessage.getContent())
                    .sources(sourceInfos)
                    .createdAt(aiMessage.getCreatedAt())
                    .build();

            long costTime = System.currentTimeMillis() - startTime;
            log.info("[RagChatAgent.chat] 方法执行完成 | conversationId={}, totalCostTime={}ms, responseLength={}",
                    conversationId, costTime, response.getContent() != null ? response.getContent().length() : 0);

            return response;

        } catch (Exception e) {
            log.error("[RagChatAgent.chat] 系统异常 | userId={}, conversationId={}, error={}, stackTrace={}",
                    userId, conversationId, e.getMessage(), e.getStackTrace());
            throw e;
        }
    }

    public MessageResponse chatWithFile(String userId, String fileId, String message) {
        long startTime = System.currentTimeMillis();
        log.info("[RagChatAgent.chatWithFile] 方法开始 | userId={}, fileId={}, messageLength={}",
                userId, fileId, message != null ? message.length() : 0);

        try {
            // 创建与特定文件关联的对话
            log.debug("[RagChatAgent.chatWithFile] 创建与文件关联的对话 | userId={}, fileId={}", userId, fileId);
            Conversation conversation = contextManager.createConversation(userId, fileId);
            log.info("[RagChatAgent.chatWithFile] 文件关联对话已创建 | conversationId={}, fileId={}",
                    conversation.getId(), fileId);

            // 添加用户消息
            log.debug("[RagChatAgent.chatWithFile] 添加用户消息 | conversationId={}", conversation.getId());
            contextManager.addMessage(conversation.getId(), "user", message, null);

            // 意图识别（强制为文档问答）
            log.debug("[RagChatAgent.chatWithFile] 设置文档问答意图 | conversationId={}", conversation.getId());
            IntentResult intentResult = IntentResult.builder()
                    .intent(Intent.DOCUMENT_QA)
                    .confidence(0.9)
                    .entities(Map.of("query", message, "fileIds", List.of(fileId)))
                    .build();
            log.info("[RagChatAgent.chatWithFile] 意图已设置 | intent={}, confidence={}",
                    intentResult.getIntent(), intentResult.getConfidence());

            // 执行技能
            log.debug("[RagChatAgent.chatWithFile] 开始执行技能 | conversationId={}, intent={}",
                    conversation.getId(), intentResult.getIntent());
            long skillStartTime = System.currentTimeMillis();

            SkillResult skillResult = skillScheduler.executeSkill(
                    intentResult.getIntent(),
                    intentResult.getEntities(),
                    userId,
                    conversation.getId()
            );

            long skillCostTime = System.currentTimeMillis() - skillStartTime;
            log.info("[RagChatAgent.chatWithFile] 技能执行完成 | conversationId={}, success={}, skillCostTime={}ms",
                    conversation.getId(), skillResult.isSuccess(), skillCostTime);

            // 添加AI响应
            List<Map<String, Object>> sources = skillResult.getSources();
            log.debug("[RagChatAgent.chatWithFile] 添加AI响应 | conversationId={}, sourcesCount={}",
                    conversation.getId(), sources != null ? sources.size() : 0);

            var aiMessage = contextManager.addMessage(conversation.getId(), "assistant",
                    skillResult.getContent(), sources);

            // 构建响应
            List<MessageResponse.SourceInfo> sourceInfos = null;
            if (sources != null && !sources.isEmpty()) {
                sourceInfos = sources.stream()
                        .map(s -> MessageResponse.SourceInfo.builder()
                                .fileId((String) s.get("fileId"))
                                .filename((String) s.getOrDefault("filename", ""))
                                .chunkText((String) s.get("chunkText"))
                                .build())
                        .toList();
            }

            MessageResponse response = MessageResponse.builder()
                    .id(aiMessage.getId())
                    .conversationId(conversation.getId())
                    .role(aiMessage.getRole())
                    .content(aiMessage.getContent())
                    .sources(sourceInfos)
                    .createdAt(aiMessage.getCreatedAt())
                    .build();

            long costTime = System.currentTimeMillis() - startTime;
            log.info("[RagChatAgent.chatWithFile] 方法执行完成 | conversationId={}, fileId={}, totalCostTime={}ms",
                    conversation.getId(), fileId, costTime);

            return response;

        } catch (Exception e) {
            log.error("[RagChatAgent.chatWithFile] 系统异常 | userId={}, fileId={}, error={}, stackTrace={}",
                    userId, fileId, e.getMessage(), e.getStackTrace());
            throw e;
        }
    }
}
