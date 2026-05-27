package com.example.ragchat.controller;

import com.example.ragchat.dto.request.MessageRequest;
import com.example.ragchat.dto.response.ConversationDetailResponse;
import com.example.ragchat.dto.response.ConversationListResponse;
import com.example.ragchat.dto.response.MessageResponse;
import com.example.ragchat.entity.Conversation;
import com.example.ragchat.entity.Message;
import com.example.ragchat.repository.ConversationRepository;
import com.example.ragchat.repository.MessageRepository;
import com.example.ragchat.service.ai.ContextManager;
import com.example.ragchat.service.ai.RagChatAgent;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final RagChatAgent ragChatAgent;
    private final ContextManager contextManager;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    @GetMapping
    public ResponseEntity<ConversationListResponse> listConversations(
            @RequestParam(value = "page", defaultValue = "0") Integer page,
            @RequestParam(value = "size", defaultValue = "20") Integer size,
            Authentication authentication) {
        String userId = authentication.getName();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "lastMessageAt"));

        var conversations = conversationRepository.findByUserIdAndIsArchivedFalse(userId, pageable);
        
        ConversationListResponse response = ConversationListResponse.builder()
                .conversations(conversations.getContent().stream()
                        .map(c -> ConversationListResponse.ConversationSummary.builder()
                                .id(c.getId())
                                .fileId(c.getFileId())
                                .title(c.getTitle())
                                .isArchived(c.getIsArchived())
                                .messageCount((int) messageRepository.countByConversationId(c.getId()))
                                .build())
                        .toList())
                .total(conversations.getTotalElements())
                .page(page)
                .size(size)
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<ConversationDetailResponse> getConversation(
            @PathVariable String conversationId,
            Authentication authentication) {
        String userId = authentication.getName();
        
        Conversation conversation = contextManager.getConversation(conversationId, userId);
        List<Message> messages = contextManager.getConversationHistory(conversationId);

        ConversationDetailResponse response = ConversationDetailResponse.builder()
                .id(conversation.getId())
                .userId(conversation.getUserId())
                .fileId(conversation.getFileId())
                .title(conversation.getTitle())
                .isArchived(conversation.getIsArchived())
                .messages(messages.stream()
                        .map(m -> MessageResponse.builder()
                                .id(m.getId())
                                .conversationId(m.getConversationId())
                                .role(m.getRole())
                                .content(m.getContent())
                                .createdAt(m.getCreatedAt())
                                .build())
                        .toList())
                .createdAt(conversation.getCreatedAt())
                .lastMessageAt(conversation.getLastMessageAt())
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<MessageResponse> createConversation(
            @Valid @RequestBody MessageRequest request,
            @RequestParam(value = "fileId", required = false) String fileId,
            Authentication authentication) {
        String userId = authentication.getName();
        
        MessageResponse response;
        if (fileId != null && !fileId.isEmpty()) {
            response = ragChatAgent.chatWithFile(userId, fileId, request.getContent());
        } else {
            response = ragChatAgent.chat(userId, null, request.getContent());
        }
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<MessageResponse> sendMessage(
            @PathVariable String conversationId,
            @Valid @RequestBody MessageRequest request,
            Authentication authentication) {
        String userId = authentication.getName();
        
        MessageResponse response = ragChatAgent.chat(userId, conversationId, request.getContent());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Void> deleteConversation(
            @PathVariable String conversationId,
            Authentication authentication) {
        String userId = authentication.getName();
        contextManager.deleteConversation(conversationId, userId);
        return ResponseEntity.noContent().build();
    }
}
