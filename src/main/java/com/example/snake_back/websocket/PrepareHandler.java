package com.example.snake_back.websocket;

import com.example.snake_back.manager.SessionContextManager;
import com.example.snake_back.service.Impl.PrepareServiceImpl;
import com.example.snake_back.pojo.dto.SessionContextDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

@Component
public class PrepareHandler {
    private final ObjectMapper objectMapper;
    private final PrepareServiceImpl prepareService;
    private final SessionContextManager sessionContextManager;

    public PrepareHandler(ObjectMapper objectMapper, PrepareServiceImpl prepareService, SessionContextManager sessionContextManager) {
        this.objectMapper = objectMapper;
        this.prepareService = prepareService;
        this.sessionContextManager = sessionContextManager;
    }

    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> data = objectMapper.readValue(message.getPayload(), Map.class);
        String type = (String) data.get("type");
        String sessionId = session.getId();
        SessionContextDTO sessionContext = sessionContextManager.getSessionContextMap().get(sessionId);
        if (sessionContext == null || sessionContext.getUserCode() == null) {
            return;
        }

        String userCode = sessionContext.getUserCode();
        switch (type) {
            case "unready" -> prepareService.unready(userCode);
            case "leave" -> prepareService.leaveRoom(userCode);
            case "ready" -> prepareService.ready(userCode);
        }
    }
}
