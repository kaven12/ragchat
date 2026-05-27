package com.example.ragchat.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SemanticSearchRequest {

    @NotBlank(message = "查询内容不能为空")
    private String query;

    private List<String> fileIds;

    private Integer limit = 10;
}
