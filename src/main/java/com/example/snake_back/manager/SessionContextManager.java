package com.example.snake_back.manager;

import com.example.snake_back.pojo.dto.RoomState;
import com.example.snake_back.pojo.dto.SessionContextDTO;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Data
public class SessionContextManager {
    Map<String, SessionContextDTO> sessionContextMap = new ConcurrentHashMap<>();
    Map<String, String> userCodeToSessionIdMap = new ConcurrentHashMap<>();
    /** 用户群聊"上线时间"（跨刷新保留，心跳过期后清除），用于过滤群聊消息可见范围 */
    private final Map<String, Long> userGroupChatJoinTime = new ConcurrentHashMap<>();
    /**
     * pageType → userCodes 索引，用于群聊广播 O(1) 查找 home 用户，避免遍历所有 session。
     */
    private final Map<String, Set<String>> pageTypeToUserCodes = new ConcurrentHashMap<>();
    private final RoomStateManager roomStateManager;

    // ==================== pageType 索引导助方法 ====================

    /** 获取某个 pageType 下的所有 userCode（只读快照，用于广播遍历） */
    public Set<String> getUserCodesByPageType(String pageType) {
        Set<String> set = pageTypeToUserCodes.get(pageType);
        if (set == null || set.isEmpty()) {
            return Collections.emptySet();
        }
        return new HashSet<>(set); // 快照，避免广播过程中并发修改
    }

    /** 注册/更新 session 时更新索引 */
    public void indexSession(String userCode, String pageType) {
        if (userCode == null || pageType == null) return;
        // 先从所有旧 pageType 中移除（处理 pageType 变更）
        removeUserFromAllPageTypes(userCode);
        addUserToPageType(userCode, pageType);
    }

    /** session 移除时清理索引（同步清理，不等延迟任务） */
    public void unindexSession(String userCode, String pageType) {
        if (userCode == null || pageType == null) return;
        removeUserFromPageType(userCode, pageType);
    }

    /** pageType 变更时更新索引 */
    public void moveSessionPageType(String userCode, String fromPageType, String toPageType) {
        if (userCode == null) return;
        if (fromPageType != null) {
            removeUserFromPageType(userCode, fromPageType);
        }
        if (toPageType != null) {
            addUserToPageType(userCode, toPageType);
        }
    }

    private void addUserToPageType(String userCode, String pageType) {
        pageTypeToUserCodes.computeIfAbsent(pageType, k -> ConcurrentHashMap.newKeySet()).add(userCode);
    }

    private void removeUserFromPageType(String userCode, String pageType) {
        Set<String> set = pageTypeToUserCodes.get(pageType);
        if (set != null) {
            set.remove(userCode);
            // 空 set 不主动删除，保留 key 避免重复 computeIfAbsent
        }
    }

    private void removeUserFromAllPageTypes(String userCode) {
        for (Set<String> set : pageTypeToUserCodes.values()) {
            set.remove(userCode);
        }
    }

    // ==================== 原有方法 ====================

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
