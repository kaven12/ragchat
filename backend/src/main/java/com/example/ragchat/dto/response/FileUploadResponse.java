package com.example.ragchat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResponse {

    private String fileId;
    private String filename;
    private Long fileSize;
    private String storageKey;

    public static FileUploadResponse of(String fileId, String filename, 
                                        Long fileSize, String storageKey) {
        return FileUploadResponse.builder()
                .fileId(fileId)
                .filename(filename)
                .fileSize(fileSize)
                .storageKey(storageKey)
                .build();
    }
}
