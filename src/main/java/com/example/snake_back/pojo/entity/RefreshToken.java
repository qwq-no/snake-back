package com.example.snake_back.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@TableName("refresh_tokens")
@Data
public class RefreshToken {
    private String id;            // UUID string for the session row
    private String userId;        // user.id (UUID string)
    private String tokenHash;     // SHA-256 hex string
    private String issuedAt;
    private String expiresAt;
    private Boolean revoked;
    private String revokedAt;
    private String replacedBy;    // session id of replacement
    private String deviceInfo;
    private String ip;
    private String lastUsedAt;
    private String createdAt;
    private String updatedAt;
}