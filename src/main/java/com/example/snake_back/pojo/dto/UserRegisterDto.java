package com.example.snake_back.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 注册请求 DTO（与实体字段格式一致，未添加校验注解）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisterDto {
    private String id;          // 可选：通常由后端生成
    private String username;
    private String email;
    private String password;    // 明文密码，由后端哈希后存入 passwordHash
    private String displayName;
}