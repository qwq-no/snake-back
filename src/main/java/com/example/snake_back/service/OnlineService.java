package com.example.snake_back.service;

import com.example.snake_back.pojo.dto.RoomState;

public interface OnlineService {
    void leaveRoom(String userCode);
    void input(String userCode, String key);
    void sendEmoji(String userCode, int emojiId);
    void assignHumanSnakeToNewPlayer(RoomState roomState, String userCode);
}
