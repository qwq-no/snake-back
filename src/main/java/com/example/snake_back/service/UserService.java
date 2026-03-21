package com.example.snake_back.service;

import com.example.snake_back.pojo.dto.UserLoginDto;
import com.example.snake_back.pojo.dto.UserRegisterDto;

public interface UserService {
    String login(UserLoginDto dto,String deviceInfo,String ip);
    void register(UserRegisterDto dto);
}

