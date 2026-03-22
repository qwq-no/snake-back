package com.example.snake_back.service;

import com.example.snake_back.pojo.dto.UserLoginDto;
import com.example.snake_back.pojo.dto.UserRegisterDto;

import java.util.Map;

public interface UserService {
    Map<String,Object> login(UserLoginDto dto, String deviceInfo, String ip);
    void register(UserRegisterDto dto);
}

