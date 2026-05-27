package com.example.ragchat.controller;

import com.example.ragchat.dto.request.SemanticSearchRequest;
import com.example.ragchat.dto.response.SearchResponse;
import com.example.ragchat.mcp.SearchMCP;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchMCP searchMCP;

    @PostMapping("/semantic")
    public ResponseEntity<SearchResponse> semanticSearch(@Valid @RequestBody SemanticSearchRequest request) {
        log.info("Semantic search query: {}", request.getQuery());

        // 生成查询向量
        float[] queryEmbedding = generateEmbedding(request.getQuery());
        
        // 执行搜索
        List<SearchMCP.SearchResult> results = searchMCP.search(
                queryEmbedding, 
                request.getLimit(), 
                request.getFileIds()
        );

        SearchResponse response = SearchResponse.builder()
                .results(results.stream()
                        .map(r -> SearchResponse.SearchResult.builder()
                                .fileId(r.getFileId())
                                .filename("")
                                .snippet(r.getChunkText())
                                .score(r.getScore())
                                .build())
                        .toList())
                .total((long) results.size())
                .build();

        return ResponseEntity.ok(response);
    }

    private float[] generateEmbedding(String text) {
        // 实际实现应该调用嵌入模型
        float[] embedding = new float[1024];
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = (float) (Math.random() * 2 - 1);
        }
        return embedding;
    }
}
