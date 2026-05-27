package com.example.ragchat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitUploadResponse {

    private String uploadId;
    private String storageKey;
    private Integer partSize;
    private Integer totalParts;
    private String uploadUrl;

    public static InitUploadResponse of(String uploadId, String storageKey, 
                                       Integer partSize, Integer totalParts, String uploadUrl) {
        return InitUploadResponse.builder()
                .uploadId(uploadId)
                .storageKey(storageKey)
                .partSize(partSize)
                .totalParts(totalParts)
                .uploadUrl(uploadUrl)
                .build();
    }
}
