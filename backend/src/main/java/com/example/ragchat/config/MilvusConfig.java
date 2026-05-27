package com.example.ragchat.config;

import io.milvus.client.MilvusClient;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class MilvusConfig {

    @Value("${milvus.host:localhost}")
    private String milvusHost;

    @Value("${milvus.port:19530}")
    private Integer milvusPort;

    @Bean
    public MilvusClient milvusClient() {
        try {
            ConnectParam connectParam = ConnectParam.newBuilder()
                    .withHost(milvusHost)
                    .withPort(milvusPort)
                    .build();
            
            MilvusClient client = new MilvusServiceClient(connectParam);
            log.info("Successfully connected to Milvus at {}:{}", milvusHost, milvusPort);
            return client;
        } catch (Exception e) {
            log.error("Failed to connect to Milvus", e);
            throw new RuntimeException("Failed to connect to Milvus", e);
        }
    }
}
