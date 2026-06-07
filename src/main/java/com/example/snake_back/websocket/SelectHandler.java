package com.example.snake_back.websocket;

import com.example.snake_back.manager.SessionContextManager;
import com.example.snake_back.service.SelectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

@Component
public class SelectHandler {
    private final ObjectMapper objectMapper;
    private final SessionContextManager sessionContextManager;
    private final SelectService selectService;

    public SelectHandler(ObjectMapper objectMapper, SelectService selectService, SessionContextManager sessionContextManager) {
        this.objectMapper = objectMapper;
        this.selectService = selectService;
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
            case "join" -> selectService.joinRoom(Integer.parseInt(String.valueOf(data.get("roomCode"))), userCode);
            case "view" -> selectService.roomSummary();
        }
    }
}
