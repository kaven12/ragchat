package com.example.ragchat.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompleteUploadRequest {

    @NotBlank(message = "Upload ID不能为空")
    private String uploadId;

    @NotEmpty(message = "分片列表不能为空")
    private List<PartInfo> parts;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PartInfo {
        private int partNumber;
        private String etag;
    }
}
