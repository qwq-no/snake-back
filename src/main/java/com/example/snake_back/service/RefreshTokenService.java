package com.example.snake_back.service;

import com.example.snake_back.pojo.entity.RefreshToken;

import java.util.Map;

public interface RefreshTokenService {
    String createAndSaveRefreshToken(String userId, String web, String s);
    RefreshToken validateRefreshToken(String tokenPlain);
    Map<String,Object> validateAndRotate(String oldPlain, String ip, String deviceInfo);
    boolean revokeByPlain(String tokenPlain);
    int revokeAllForUser(String userId);
}
