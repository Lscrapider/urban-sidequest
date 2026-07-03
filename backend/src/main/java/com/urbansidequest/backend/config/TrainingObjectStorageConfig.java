package com.urbansidequest.backend.config;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TrainingObjectStorageConfig {

    @Bean
    public MinioClient routePreferenceTrainingMinioClient(RoutePreferenceTrainingStorageProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }
}
