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
        // 如果没有对话ID，创建新对话
        if (conversationId == null || conversationId.isEmpty()) {
            Conversation conversation = contextManager.createConversation(userId, null);
            conversationId = conversation.getId();
        }

        // 添加用户消息到上下文
        contextManager.addMessage(conversationId, "user", message, null);

        // 1. 意图识别
        IntentResult intentResult = intentRecognizer.recognize(message);
        log.info("Intent recognized: {} (confidence: {})", intentResult.getIntent(), intentResult.getConfidence());

        // 2. 技能调度执行
        SkillResult skillResult = skillScheduler.executeSkill(
                intentResult.getIntent(),
                intentResult.getEntities(),
                userId,
                conversationId
        );

        // 3. 添加AI响应到上下文
        List<Map<String, Object>> sources = skillResult.getSources();
        var aiMessage = contextManager.addMessage(conversationId, "assistant", 
                skillResult.getContent(), sources);

        // 4. 构建响应
        return MessageResponse.builder()
                .id(aiMessage.getId())
                .conversationId(conversationId)
                .role(aiMessage.getRole())
                .content(aiMessage.getContent())
                .sources(sources != null ? sources.stream()
                        .map(s -> MessageResponse.SourceInfo.builder()
                                .fileId((String) s.get("fileId"))
                                .filename("")
                                .chunkText((String) s.get("chunkText"))
                                .build())
                        .toList() : null)
                .createdAt(aiMessage.getCreatedAt())
                .build();
    }

    public MessageResponse chatWithFile(String userId, String fileId, String message) {
        // 创建与特定文件关联的对话
        Conversation conversation = contextManager.createConversation(userId, fileId);
        
        // 添加用户消息
        contextManager.addMessage(conversation.getId(), "user", message, null);

        // 意图识别（强制为文档问答）
        IntentResult intentResult = IntentResult.builder()
                .intent(Intent.DOCUMENT_QA)
                .confidence(0.9)
                .entities(Map.of("query", message, "fileIds", List.of(fileId)))
                .build();

        // 执行技能
        SkillResult skillResult = skillScheduler.executeSkill(
                intentResult.getIntent(),
                intentResult.getEntities(),
                userId,
                conversation.getId()
        );

        // 添加AI响应
        List<Map<String, Object>> sources = skillResult.getSources();
        var aiMessage = contextManager.addMessage(conversation.getId(), "assistant", 
                skillResult.getContent(), sources);

        return MessageResponse.builder()
                .id(aiMessage.getId())
                .conversationId(conversation.getId())
                .role(aiMessage.getRole())
                .content(aiMessage.getContent())
                .sources(sources != null ? sources.stream()
                        .map(s -> MessageResponse.SourceInfo.builder()
                                .fileId((String) s.get("fileId"))
                                .filename("")
                                .chunkText((String) s.get("chunkText"))
                                .build())
                        .toList() : null)
                .createdAt(aiMessage.getCreatedAt())
                .build();
    }
}
