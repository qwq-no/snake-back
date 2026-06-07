package com.example.snake_back.service;

import com.example.snake_back.pojo.dto.SessionContextDTO;
import org.springframework.web.socket.WebSocketSession;

public interface SessionContextService {
    void registerSession(WebSocketSession session, String userCode, String pageType);

    void updateHeartbeat(String sessionId);

    SessionContextDTO getSessionContext(String sessionId);

    void removeSession(String sessionId);
}