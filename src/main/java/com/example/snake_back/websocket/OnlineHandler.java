package com.example.snake_back.websocket;

import com.example.snake_back.manager.SessionContextManager;
import com.example.snake_back.service.OnlineService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.util.Map;

@Component
public class OnlineHandler{
    private final ObjectMapper objectMapper;
    private final OnlineService onlineService;
    private final SessionContextManager sessionContextManager;
    public OnlineHandler(OnlineService onlineService, ObjectMapper objectMapper, SessionContextManager sessionContextManager) {
        this.onlineService = onlineService;
        this.objectMapper = objectMapper;
        this.sessionContextManager = sessionContextManager;
    }

    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> data = objectMapper.readValue(message.getPayload(), Map.class);
        String type = (String) data.get("type");
        String sessionId = session.getId();
        var context = sessionContextManager.getSessionContextMap().get(sessionId);
        if (context == null || context.getUserCode() == null) {
            return;
        }

        String userCode = context.getUserCode();
        switch (type) {
            case "leave" -> onlineService.leaveRoom(userCode);
            case "input" -> onlineService.input(userCode, (String) data.get("key"));
            case "emoji" -> onlineService.sendEmoji(userCode, (int) data.get("emojiId"));
        }
    }
}