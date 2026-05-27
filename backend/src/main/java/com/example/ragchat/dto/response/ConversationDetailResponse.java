package com.example.ragchat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDetailResponse {

    private String id;
    private String userId;
    private String fileId;
    private String title;
    private Boolean isArchived;
    private List<MessageResponse> messages;
    private LocalDateTime createdAt;
    private LocalDateTime lastMessageAt;
}
