package com.example.snake_back.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@TableName("users")
@Data
public class User {
    private String id;            // UUID string
    private String username;
    private String email;
    private String passwordHash;  // Argon2/bcrypt hash
    private String displayName;
    private Boolean isActive;
    private Integer failedLoginCount;
    private String lockoutUntil;  // optional, could be Instant/String depending on mapping
    private String passwordChangedAt;
    private String lastLoginAt;
    private String createdAt;
    private String updatedAt;
}