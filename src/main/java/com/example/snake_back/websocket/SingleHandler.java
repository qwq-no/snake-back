package com.example.snake_back.websocket;

import com.example.snake_back.manager.SessionContextManager;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Component
public class SingleHandler {
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    }
}
