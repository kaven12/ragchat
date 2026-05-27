package com.example.ragchat.mcp;

import io.milvus.client.MilvusClient;
import io.milvus.param.*;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchMCP {

    private final MilvusClient milvusClient;

    @Value("${milvus.search.nprobe:10}")
    private Integer nprobe;

    private static final String COLLECTION_NAME = "file_embeddings";
    private static final String VECTOR_FIELD = "embedding";
    private static final String FILE_ID_FIELD = "file_id";
    private static final String CHUNK_TEXT_FIELD = "chunk_text";

    public void ensureCollectionExists() {
        try {
            HasCollectionParam hasParam = HasCollectionParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .build();
            R<Boolean> hasResult = milvusClient.hasCollection(hasParam);
            
            if (!hasResult.getData()) {
                createCollection();
            }
        } catch (Exception e) {
            log.error("Failed to check collection existence", e);
        }
    }

    private void createCollection() {
        List<FieldType> fields = new ArrayList<>();
        
        // 文件ID字段
        fields.add(FieldType.newBuilder()
                .withName("id")
                .withDataType(DataType.Int64)
                .withPrimaryKey(true)
                .withAutoID(true)
                .build());
        
        // 文件ID
        fields.add(FieldType.newBuilder()
                .withName(FILE_ID_FIELD)
                .withDataType(DataType.VarChar)
                .withMaxLength(64)
                .build());
        
        // 文本片段
        fields.add(FieldType.newBuilder()
                .withName(CHUNK_TEXT_FIELD)
                .withDataType(DataType.VarChar)
                .withMaxLength(4096)
                .build());
        
        // 向量字段
        fields.add(FieldType.newBuilder()
                .withName(VECTOR_FIELD)
                .withDataType(DataType.FloatVector)
                .withDimension(1024)
                .build());

        CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withFields(fields)
                .build();

        R<RpcStatus> result = milvusClient.createCollection(createParam);
        if (result.getStatus() != R.Status.Success.getCode()) {
            log.error("Failed to create collection: {}", result.getMessage());
        } else {
            // 创建索引
            createIndex();
        }
    }

    private void createIndex() {
        IndexParam indexParam = IndexParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withFieldName(VECTOR_FIELD)
                .withIndexType(IndexType.IVF_FLAT)
                .withMetricType(MetricType.L2)
                .withExtraParam("{\"nlist\": 1024}")
                .build();

        R<RpcStatus> result = milvusClient.createIndex(indexParam);
        if (result.getStatus() != R.Status.Success.getCode()) {
            log.error("Failed to create index: {}", result.getMessage());
        }
    }

    public void insertEmbedding(String fileId, String chunkText, float[] embedding) {
        ensureCollectionExists();

        List<InsertParam.Field> fields = new ArrayList<>();
        fields.add(new InsertParam.Field(FILE_ID_FIELD, List.of(fileId)));
        fields.add(new InsertParam.Field(CHUNK_TEXT_FIELD, List.of(chunkText)));
        fields.add(new InsertParam.Field(VECTOR_FIELD, List.of(embedding)));

        InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withFields(fields)
                .build();

        R<InsertResponse> result = milvusClient.insert(insertParam);
        if (result.getStatus() != R.Status.Success.getCode()) {
            log.error("Failed to insert embedding: {}", result.getMessage());
            throw new RuntimeException("Failed to insert embedding");
        }
    }

    public List<SearchResult> search(float[] queryVector, int limit, List<String> fileIds) {
        ensureCollectionExists();

        List<String> outputFields = List.of(FILE_ID_FIELD, CHUNK_TEXT_FIELD);

        SearchParam.Builder builder = SearchParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withMetricType(MetricType.L2)
                .withTopK(limit)
                .withVectors(List.of(queryVector))
                .withVectorFieldName(VECTOR_FIELD)
                .withOutFields(outputFields)
                .withParams("{\"nprobe\": " + nprobe + "}");

        // 如果指定了文件ID，添加过滤条件
        if (fileIds != null && !fileIds.isEmpty()) {
            String filter = String.format("%s in [%s]", 
                    FILE_ID_FIELD, 
                    fileIds.stream().map(id -> "\"" + id + "\"").reduce((a, b) -> a + "," + b).orElse(""));
            builder.withExpr(filter);
        }

        R<SearchResults> result = milvusClient.search(builder.build());
        if (result.getStatus() != R.Status.Success.getCode()) {
            log.error("Search failed: {}", result.getMessage());
            return List.of();
        }

        List<SearchResult> results = new ArrayList<>();
        SearchResults.DataWrapper wrapper = result.getData().getResults().get(0);
        
        for (int i = 0; i < wrapper.getRowCount(); i++) {
            results.add(SearchResult.builder()
                    .fileId(wrapper.getFieldData(FILE_ID_FIELD).get(i).toString())
                    .chunkText(wrapper.getFieldData(CHUNK_TEXT_FIELD).get(i).toString())
                    .score(wrapper.getScores().get(i))
                    .build());
        }

        return results;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SearchResult {
        private String fileId;
        private String chunkText;
        private Double score;
    }
}
