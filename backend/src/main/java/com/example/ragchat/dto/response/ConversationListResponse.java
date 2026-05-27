package com.example.ragchat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationListResponse {

    private List<ConversationSummary> conversations;
    private Long total;
    private Integer page;
    private Integer size;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversationSummary {
        private String id;
        private String fileId;
        private String title;
        private Boolean isArchived;
        private Integer messageCount;
    }
}
