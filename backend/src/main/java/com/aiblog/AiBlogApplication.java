package com.aiblog;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.aiblog.mapper")
public class AiBlogApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiBlogApplication.class, args);
    }
}
