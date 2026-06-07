package com.example.snake_back;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.example.snake_back.mapper")
public class SnakeBackApplication {

    public static void main(String[] args) {
        SpringApplication.run(SnakeBackApplication.class, args);
    }

}
