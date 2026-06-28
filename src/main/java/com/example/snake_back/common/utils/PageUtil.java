package com.example.snake_back.common.utils;

import com.example.snake_back.manager.RoomStateManager;
import com.example.snake_back.manager.SessionContextManager;
import com.example.snake_back.pojo.dto.SessionContextDTO;
import com.example.snake_back.service.BroadcastService;
import org.springframework.stereotype.Component;


@Component
public class PageUtil {
    private final SessionContextManager sessionContextManager;
    private final RoomStateManager roomStateManager;
    private final BroadcastService broadcastService;

    public PageUtil(SessionContextManager sessionContextManager, RoomStateManager roomStateManager, BroadcastService broadcastService) {
        this.sessionContextManager = sessionContextManager;
        this.roomStateManager = roomStateManager;
        this.broadcastService = broadcastService;
    }

    public void changePage(String userCode, String pageType, boolean sendSnapshot) {
        if (userCode == null || userCode.isBlank() || pageType == null || pageType.isBlank()) {
            return;
        }

        String sessionId = sessionContextManager.getUserCodeToSessionIdMap().get(userCode);
        if (sessionId == null) {
            return;
        }

        SessionContextDTO sessionContextDTO = sessionContextManager.getSessionContextMap().get(sessionId);
        if (sessionContextDTO == null) {
            return;
        }

        String fromPage = sessionContextDTO.getPageType();
        sessionContextDTO.setPageType(pageType);
        sessionContextManager.moveSessionPageType(userCode, fromPage, pageType);

        // 只有房间页面才保留 roomCode，切到 home/select 等页面时清掉
        if ("prepare".equals(pageType) || "online".equals(pageType)) {
            Integer roomCode = roomStateManager.getRoomCodeByUserCode(userCode);
            sessionContextDTO.setRoomCode(roomCode == null ? null : String.valueOf(roomCode));
        } else {
            sessionContextDTO.setRoomCode(null);
        }
        sessionContextManager.fitStatus(sessionId);

        if (sendSnapshot) {
            pushSnapshot(userCode, pageType);
        }
        broadcastService.broadcastFriendStatusChange(userCode);
    }

    public void syncPage(String userCode, String key) {
        if (key == null || key.isEmpty()) {
            return;
        }
        changePage(userCode, key, true);
    }
    private void pushSnapshot(String userCode, String pageType) {
        if ("home".equals(pageType)) {
            broadcastService.sendHomeFriendStatuses(userCode);
            return;
        }
        if ("select".equals(pageType)) {
            broadcastService.sendRoomSummaries(userCode);
            return;
        }

        Integer roomCode = roomStateManager.getRoomCodeByUserCode(userCode);
        if (roomCode == null) {
            return;
        }

        if ("prepare".equals(pageType)) {
            broadcastService.broadcastRoomLobbyState(roomCode);
        } else if ("online".equals(pageType)) {
            broadcastService.broadcastRoomState(roomCode);
        }
    }
}
