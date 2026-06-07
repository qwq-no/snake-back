package com.example.snake_back.service.Impl;

import com.example.snake_back.service.BroadcastService;
import com.example.snake_back.service.HomeService;
import org.springframework.stereotype.Service;

@Service
public class HomeServiceImpl implements HomeService {
    private final BroadcastService broadcastService;

    public HomeServiceImpl(BroadcastService broadcastService) {
        this.broadcastService = broadcastService;
    }

    @Override
    public void sendHomeFriendStatuses(String userCode) {
        broadcastService.sendHomeFriendStatuses(userCode);
    }
}
