package com.urbansidequest.backend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.urbansidequest.backend.mapper")
@SpringBootApplication
public class UrbanSidequestBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(UrbanSidequestBackendApplication.class, args);
    }
}
