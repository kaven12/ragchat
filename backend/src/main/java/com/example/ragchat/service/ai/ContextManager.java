package com.example.ragchat.service.ai;

import com.example.ragchat.entity.Conversation;
import com.example.ragchat.entity.Message;
import com.example.ragchat.repository.ConversationRepository;
import com.example.ragchat.repository.MessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContextManager {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    public Conversation createConversation(String userId, String fileId) {
        long startTime = System.currentTimeMillis();
        log.info("[ContextManager.createConversation] 方法开始 | userId={}, fileId={}", userId, fileId);

        try {
            String conversationId = UUID.randomUUID().toString();
            log.debug("[ContextManager.createConversation] 生成对话ID | conversationId={}", conversationId);

            Conversation conversation = Conversation.builder()
                    .id(conversationId)
                    .userId(userId)
                    .fileId(fileId)
                    .title("新对话")
                    .isArchived(false)
                    .createdAt(LocalDateTime.now())
                    .build();

            Conversation saved = conversationRepository.save(conversation);
            log.info("[ContextManager.createConversation] 对话创建成功 | conversationId={}, userId={}, fileId={}",
                    saved.getId(), userId, fileId);

            long costTime = System.currentTimeMillis() - startTime;
            log.debug("[ContextManager.createConversation] 方法执行完成 | conversationId={}, 耗时={}ms", conversationId, costTime);

            return saved;

        } catch (Exception e) {
            log.error("[ContextManager.createConversation] 对话创建异常 | userId={}, error={}, stackTrace={}",
                    userId, e.getMessage(), e.getStackTrace());
            throw new RuntimeException("创建对话失败", e);
        }
    }

    public Conversation getConversation(String conversationId, String userId) {
        long startTime = System.currentTimeMillis();
        log.debug("[ContextManager.getConversation] 方法开始 | conversationId={}, userId={}", conversationId, userId);

        try {
            Conversation conversation = conversationRepository.findByIdAndUserId(conversationId, userId)
                    .orElseThrow(() -> {
                        log.error("[ContextManager.getConversation] 对话不存在 | conversationId={}, userId={}", conversationId, userId);
                        return new RuntimeException("对话不存在");
                    });

            long costTime = System.currentTimeMillis() - startTime;
            log.debug("[ContextManager.getConversation] 方法执行完成 | conversationId={}, 耗时={}ms", conversationId, costTime);

            return conversation;

        } catch (Exception e) {
            log.error("[ContextManager.getConversation] 获取对话异常 | conversationId={}, error={}", conversationId, e.getMessage());
            throw e;
        }
    }

    public List<Message> getConversationHistory(String conversationId) {
        long startTime = System.currentTimeMillis();
        log.debug("[ContextManager.getConversationHistory] 方法开始 | conversationId={}", conversationId);

        try {
            List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
            log.info("[ContextManager.getConversationHistory] 历史消息获取成功 | conversationId={}, messageCount={}",
                    conversationId, messages.size());

            long costTime = System.currentTimeMillis() - startTime;
            log.debug("[ContextManager.getConversationHistory] 方法执行完成 | conversationId={}, 耗时={}ms", conversationId, costTime);

            return messages;

        } catch (Exception e) {
            log.error("[ContextManager.getConversationHistory] 获取历史消息异常 | conversationId={}, error={}",
                    conversationId, e.getMessage());
            throw new RuntimeException("获取历史消息失败", e);
        }
    }

    public Message addMessage(String conversationId, String role, String content,
                              List<Map<String, Object>> sources) {
        long startTime = System.currentTimeMillis();
        log.debug("[ContextManager.addMessage] 方法开始 | conversationId={}, role={}, contentLength={}",
                conversationId, role, content != null ? content.length() : 0);

        try {
            String messageId = UUID.randomUUID().toString();
            log.debug("[ContextManager.addMessage] 生成消息ID | messageId={}", messageId);

            Message message = Message.builder()
                    .id(messageId)
                    .conversationId(conversationId)
                    .role(role)
                    .content(content)
                    .sources(sources != null ? serializeSources(sources) : "[]")
                    .createdAt(LocalDateTime.now())
                    .build();

            Message saved = messageRepository.save(message);
            log.info("[ContextManager.addMessage] 消息保存成功 | messageId={}, conversationId={}, role={}",
                    messageId, conversationId, role);

            // 更新对话的最后消息时间
            log.debug("[ContextManager.addMessage] 更新对话最后消息时间 | conversationId={}", conversationId);
            conversationRepository.findById(conversationId).ifPresent(conversation -> {
                conversation.setLastMessageAt(LocalDateTime.now());
                conversationRepository.save(conversation);
                log.debug("[ContextManager.addMessage] 对话更新时间已更新 | conversationId={}, lastMessageAt={}",
                        conversationId, conversation.getLastMessageAt());
            });

            long costTime = System.currentTimeMillis() - startTime;
            log.debug("[ContextManager.addMessage] 方法执行完成 | messageId={}, 耗时={}ms", messageId, costTime);

            return saved;

        } catch (Exception e) {
            log.error("[ContextManager.addMessage] 添加消息异常 | conversationId={}, error={}, stackTrace={}",
                    conversationId, e.getMessage(), e.getStackTrace());
            throw new RuntimeException("添加消息失败", e);
        }
    }

    public void deleteConversation(String conversationId, String userId) {
        long startTime = System.currentTimeMillis();
        log.info("[ContextManager.deleteConversation] 方法开始 | conversationId={}, userId={}", conversationId, userId);

        try {
            // 获取对话
            Conversation conversation = getConversation(conversationId, userId);
            log.debug("[ContextManager.deleteConversation] 获取对话成功 | conversationId={}", conversationId);

            // 删除消息
            log.debug("[ContextManager.deleteConversation] 删除对话消息 | conversationId={}", conversationId);
            messageRepository.deleteByConversationId(conversationId);
            log.debug("[ContextManager.deleteConversation] 消息删除完成 | conversationId={}", conversationId);

            // 删除对话
            conversationRepository.delete(conversation);
            log.info("[ContextManager.deleteConversation] 对话删除成功 | conversationId={}", conversationId);

            long costTime = System.currentTimeMillis() - startTime;
            log.debug("[ContextManager.deleteConversation] 方法执行完成 | conversationId={}, 耗时={}ms", conversationId, costTime);

        } catch (Exception e) {
            log.error("[ContextManager.deleteConversation] 删除对话异常 | conversationId={}, error={}, stackTrace={}",
                    conversationId, e.getMessage(), e.getStackTrace());
            throw new RuntimeException("删除对话失败", e);
        }
    }

    public void archiveConversation(String conversationId, String userId) {
        long startTime = System.currentTimeMillis();
        log.info("[ContextManager.archiveConversation] 方法开始 | conversationId={}, userId={}", conversationId, userId);

        try {
            Conversation conversation = getConversation(conversationId, userId);
            log.debug("[ContextManager.archiveConversation] 获取对话成功 | conversationId={}, currentArchived={}",
                    conversationId, conversation.getIsArchived());

            conversation.setIsArchived(true);
            conversationRepository.save(conversation);

            log.info("[ContextManager.archiveConversation] 对话归档成功 | conversationId={}", conversationId);

            long costTime = System.currentTimeMillis() - startTime;
            log.debug("[ContextManager.archiveConversation] 方法执行完成 | conversationId={}, 耗时={}ms", conversationId, costTime);

        } catch (Exception e) {
            log.error("[ContextManager.archiveConversation] 归档对话异常 | conversationId={}, error={}", conversationId, e.getMessage());
            throw new RuntimeException("归档对话失败", e);
        }
    }

    public Map<String, Object> buildContext(String conversationId, int maxMessages) {
        long startTime = System.currentTimeMillis();
        log.debug("[ContextManager.buildContext] 方法开始 | conversationId={}, maxMessages={}", conversationId, maxMessages);

        try {
            List<Message> messages = getConversationHistory(conversationId);
            log.debug("[ContextManager.buildContext] 历史消息获取成功 | messageCount={}", messages.size());

            if (messages.size() > maxMessages) {
                messages = messages.subList(messages.size() - maxMessages, messages.size());
                log.debug("[ContextManager.buildContext] 消息已截取 | originalSize={}, truncatedSize={}", messages.size(), maxMessages);
            }

            Map<String, Object> context = new HashMap<>();
            context.put("conversationId", conversationId);
            context.put("messages", messages.stream()
                    .map(m -> {
                        Map<String, Object> msg = new HashMap<>();
                        msg.put("role", m.getRole());
                        msg.put("content", m.getContent());
                        msg.put("createdAt", m.getCreatedAt());
                        return msg;
                    })
                    .toList());

            long costTime = System.currentTimeMillis() - startTime;
            log.debug("[ContextManager.buildContext] 方法执行完成 | conversationId={}, 耗时={}ms, contextSize={}",
                    conversationId, costTime, context.size());

            return context;

        } catch (Exception e) {
            log.error("[ContextManager.buildContext] 构建上下文异常 | conversationId={}, error={}", conversationId, e.getMessage());
            throw new RuntimeException("构建上下文失败", e);
        }
    }

    private String serializeSources(List<Map<String, Object>> sources) {
        try {
            String json = objectMapper.writeValueAsString(sources);
            log.debug("[ContextManager.serializeSources] 来源序列化成功 | jsonLength={}", json.length());
            return json;
        } catch (Exception e) {
            log.warn("[ContextManager.serializeSources] 来源序列化失败 | error={}", e.getMessage());
            return "[]";
        }
    }
}
