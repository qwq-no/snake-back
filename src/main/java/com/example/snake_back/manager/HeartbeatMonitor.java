package com.example.snake_back.manager;

import com.example.snake_back.service.SessionContextService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class HeartbeatMonitor {
    private final SessionContextManager sessionContextManager;
    private final SessionContextService sessionContextService;

    public HeartbeatMonitor(SessionContextManager sessionContextManager, SessionContextService sessionContextService) {
        this.sessionContextManager = sessionContextManager;
        this.sessionContextService = sessionContextService;
    }

    @Scheduled(fixedRate = 5000)
    public void checkHeartbeat() {
        long now = System.currentTimeMillis();
        for (String sessionId : sessionContextManager.getSessionContextMap().keySet()) {
            var ctx = sessionContextManager.getSessionContextMap().get(sessionId);
            if (ctx == null) {
                continue;
            }
            if (now - ctx.getLastHeartbeat() > ctx.getHeartbeatTimeout()) {
                sessionContextService.removeSession(sessionId);
            }
        }
    }
}