package com.example.snake_back.service;

import com.example.snake_back.pojo.dto.RoomState;
import com.example.snake_back.pojo.vo.GroupChatMessageVO;
import com.example.snake_back.pojo.vo.RoomSummaryVO;

public interface BroadcastService {
    void broadcastRoomState(Integer roomCode);
    void broadcastRoomState(RoomState roomState);
    void broadcastRoomDelta(RoomState roomState);
    void broadcastRoomDebugTime(Integer roomCode, long timestamp);
    void broadcastRoomLobbyState(Integer roomCode);
    void broadcastRoomSummaries(RoomSummaryVO[] roomSummaries);
    void sendHomeFriendStatuses(String userCode);
    void broadcastFriendStatusChange(String userCode);
    void broadcastFriendStatusOffline(String userCode);
    void sendRoomSummaries(String userCode);
    void broadcastRoomSummary(RoomSummaryVO roomSummary);
    void broadcastGroupChatMessage(GroupChatMessageVO msg);
    void sendGroupChatHistory(String userCode);
}