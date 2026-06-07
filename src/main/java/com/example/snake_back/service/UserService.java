package com.example.snake_back.service;

import com.example.snake_back.pojo.dto.UserLoginDTO;
import com.example.snake_back.pojo.dto.UserRegisterDTO;

import java.util.Map;

public interface UserService {
    Map<String,Object> login(UserLoginDTO dto, String deviceInfo, String ip);
    void register(UserRegisterDTO dto);
    int compareMaxLength(int length, String userId);
    void updateDisplayName(String userId, String displayName);
    void updatePassword(String userId, String newPassword);
}

