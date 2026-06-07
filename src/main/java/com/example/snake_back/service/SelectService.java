package com.example.snake_back.service;

import com.example.snake_back.pojo.vo.RoomSummaryVO;

public interface SelectService {
    RoomSummaryVO[] getRoomSummaries();
    void joinRoom(int roomCode,String userCode);
    void roomSummary();
    void sendRoomSummaries(String userCode);
}
