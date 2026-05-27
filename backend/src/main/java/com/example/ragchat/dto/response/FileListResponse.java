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
public class FileListResponse {

    private List<FileInfo> files;
    private Long total;
    private Integer page;
    private Integer size;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileInfo {
        private String id;
        private String filename;
        private Long fileSize;
        private String fileType;
        private String directoryId;
        private String storageKey;
    }
}
