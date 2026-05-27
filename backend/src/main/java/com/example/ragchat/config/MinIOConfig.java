package com.example.ragchat.config;

import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class MinIOConfig {

    @Value("${minio.host:localhost}")
    private String minioHost;

    @Value("${minio.port:9000}")
    private Integer minioPort;

    @Value("${minio.access-key:minioadmin}")
    private String accessKey;

    @Value("${minio.secret-key:minioadmin}")
    private String secretKey;

    @Bean
    public MinioClient minioClient() {
        try {
            MinioClient client = MinioClient.builder()
                    .endpoint("http://" + minioHost + ":" + minioPort)
                    .credentials(accessKey, secretKey)
                    .build();
            log.info("Successfully connected to MinIO at {}:{}", minioHost, minioPort);
            return client;
        } catch (Exception e) {
            log.error("Failed to connect to MinIO", e);
            throw new RuntimeException("Failed to connect to MinIO", e);
        }
    }
}
