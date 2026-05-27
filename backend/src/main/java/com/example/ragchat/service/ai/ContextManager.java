package com.example.ragchat.service.ai;

import com.example.ragchat.entity.Conversation;
import com.example.ragchat.entity.Message;
import com.example.ragchat.repository.ConversationRepository;
import com.example.ragchat.repository.MessageRepository;
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

    public Conversation createConversation(String userId, String fileId) {
        Conversation conversation = Conversation.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .fileId(fileId)
                .title("新对话")
                .isArchived(false)
                .createdAt(LocalDateTime.now())
                .build();
        
        return conversationRepository.save(conversation);
    }

    public Conversation getConversation(String conversationId, String userId) {
        return conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new RuntimeException("对话不存在"));
    }

    public List<Message> getConversationHistory(String conversationId) {
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    public Message addMessage(String conversationId, String role, String content, 
                              List<Map<String, Object>> sources) {
        Message message = Message.builder()
                .id(UUID.randomUUID().toString())
                .conversationId(conversationId)
                .role(role)
                .content(content)
                .sources(sources != null ? serializeSources(sources) : "[]")
                .createdAt(LocalDateTime.now())
                .build();
        
        Message saved = messageRepository.save(message);
        
        // 更新对话的最后消息时间
        conversationRepository.findById(conversationId).ifPresent(conversation -> {
            conversation.setLastMessageAt(LocalDateTime.now());
            conversationRepository.save(conversation);
        });
        
        return saved;
    }

    public void deleteConversation(String conversationId, String userId) {
        Conversation conversation = getConversation(conversationId, userId);
        messageRepository.deleteByConversationId(conversationId);
        conversationRepository.delete(conversation);
    }

    public void archiveConversation(String conversationId, String userId) {
        Conversation conversation = getConversation(conversationId, userId);
        conversation.setIsArchived(true);
        conversationRepository.save(conversation);
    }

    public Map<String, Object> buildContext(String conversationId, int maxMessages) {
        List<Message> messages = getConversationHistory(conversationId);
        
        if (messages.size() > maxMessages) {
            messages = messages.subList(messages.size() - maxMessages, messages.size());
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
        
        return context;
    }

    private String serializeSources(List<Map<String, Object>> sources) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(sources);
        } catch (Exception e) {
            log.warn("Failed to serialize sources", e);
            return "[]";
        }
    }
}
