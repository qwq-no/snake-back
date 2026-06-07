package com.example.snake_back.service;

public interface PrepareService {
    void leaveRoom(String userCode);
    void ready(String userCode);
    void unready(String userCode);
}
