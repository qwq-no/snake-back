package com.example.snake_back.manager;

import com.example.snake_back.pojo.dto.RoomState;
import com.example.snake_back.pojo.dto.SessionContextDTO;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Data
public class SessionContextManager {
    Map<String, SessionContextDTO> sessionContextMap = new ConcurrentHashMap<>();
    Map<String, String> userCodeToSessionIdMap = new ConcurrentHashMap<>();
    /** 用户群聊"上线时间"（跨刷新保留，心跳过期后清除），用于过滤群聊消息可见范围 */
    private final Map<String, Long> userGroupChatJoinTime = new ConcurrentHashMap<>();
    private final RoomStateManager roomStateManager;

    public String fitStatus(String sessionId) {
        SessionContextDTO sessionContextDTO = getSessionContextMap().get(sessionId);
        if (sessionContextDTO == null) {
            return null;
        }

        String status = deriveStatus(sessionContextDTO);
        sessionContextDTO.setStatus(status);
        return status;
    }

    public void logSessionContextMap(String reason) {
    }

    private String deriveStatus(SessionContextDTO sessionContextDTO) {
        String pageType = normalize(sessionContextDTO.getPageType());
        if (!pageType.isEmpty()) {
            return switch (pageType) {
                case "select" -> "ROOM_SELECT";
                case "prepare" -> "ROOM_PREPARE";
                case "single" -> "IN_SINGLE_GAME";
                case "talk" -> "TALK";
                case "home" -> "HOME";
                case "online" -> "ONLINE";
                default -> pageType.toUpperCase(Locale.ROOT);
            };
        }

        Integer roomCode = parseRoomCode(sessionContextDTO.getRoomCode());
        if (roomCode != null) {
            RoomState roomState = roomStateManager == null ? null : roomStateManager.getRoomStates().get(roomCode);
            if (roomState != null && "playing".equals(roomState.getStatus())) {
                return "ONLINE";
            }
            return "ROOM_PREPARE";
        }

        String currentStatus = normalize(sessionContextDTO.getStatus());
        if (!currentStatus.isEmpty()) {
            return currentStatus;
        }

        return "HOME";
    }

    private Integer parseRoomCode(String roomCode) {
        if (roomCode == null || roomCode.isBlank()) {
            return null;
        }

        try {
            return Integer.parseInt(roomCode);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
