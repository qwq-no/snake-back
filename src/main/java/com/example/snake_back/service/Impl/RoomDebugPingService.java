package com.example.snake_back.service.Impl;

import com.example.snake_back.manager.RoomStateManager;
import com.example.snake_back.pojo.dto.RoomState;
import com.example.snake_back.service.BroadcastService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class RoomDebugPingService {
    private final RoomStateManager roomStateManager;
    private final BroadcastService broadcastService;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    public RoomDebugPingService(RoomStateManager roomStateManager, BroadcastService broadcastService) {
        this.roomStateManager = roomStateManager;
        this.broadcastService = broadcastService;
    }

    @PostConstruct
    public void start() {
        // Previous value: 1000ms. Lower debug broadcast frequency during blockage investigation.
        executor.scheduleAtFixedRate(this::broadcastPings, 2000, 2000, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void stop() {
        executor.shutdownNow();
    }

    private void broadcastPings() {
        long timestamp = System.currentTimeMillis();
        for (Map.Entry<Integer, RoomState> entry : roomStateManager.getRoomStates().entrySet()) {
            RoomState roomState = entry.getValue();
            if (roomState == null || !"playing".equals(roomState.getStatus())) {
                continue;
            }
            broadcastService.broadcastRoomDebugTime(entry.getKey(), timestamp);
        }
    }
}