package com.aiview;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.aiview.**.mapper")
public class AiviewApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiviewApplication.class, args);
    }
}