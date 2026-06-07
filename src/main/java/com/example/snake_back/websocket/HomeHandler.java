package com.example.snake_back.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Component
public class HomeHandler {
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    }
}
