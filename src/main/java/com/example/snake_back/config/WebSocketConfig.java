package com.example.snake_back.config;

import com.example.snake_back.websocket.CoreHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final CoreHandler coreHandler;

    public WebSocketConfig(CoreHandler coreHandler) {
        this.coreHandler = coreHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(coreHandler, "/ws/game")
                .setAllowedOrigins("*");
    }
}