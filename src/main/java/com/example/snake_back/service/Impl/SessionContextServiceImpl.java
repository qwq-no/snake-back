package com.example.snake_back.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.snake_back.manager.RoomStateManager;
import com.example.snake_back.manager.RoomSummaryManager;
import com.example.snake_back.manager.SessionContextManager;
import com.example.snake_back.mapper.UserMapper;
import com.example.snake_back.pojo.dto.RoomState;
import com.example.snake_back.pojo.dto.SessionContextDTO;
import com.example.snake_back.pojo.entity.User;
import com.example.snake_back.service.BroadcastService;
import com.example.snake_back.service.OnlineService;
import com.example.snake_back.service.SessionContextService;
import com.example.snake_back.websocket.ConnectionLimitInterceptor;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.util.concurrent.*;

@Slf4j
@Service
public class SessionContextServiceImpl implements SessionContextService {
    private static final long DISCONNECT_GRACE_PERIOD_MS = 2000L;
    private static final int WS_SEND_TIME_LIMIT_MS = 2000;
    private static final int WS_BUFFER_SIZE_LIMIT_BYTES = 512 * 1024;

    private final SessionContextManager sessionContextManager;
    private final RoomStateManager roomStateManager;
    private final RoomSummaryManager roomSummaryManager;
    private final BroadcastService broadcastService;
    private final OnlineService onlineService;
    private final UserMapper userMapper;
    private final ConnectionLimitInterceptor connectionLimitInterceptor;
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
    private final ConcurrentHashMap<String, ScheduledFuture<?>> pendingCleanupTasks = new ConcurrentHashMap<>();

    public SessionContextServiceImpl(SessionContextManager sessionContextManager,
                                     RoomStateManager roomStateManager,
                                     RoomSummaryManager roomSummaryManager,
                                     BroadcastService broadcastService,
                                     OnlineService onlineService,
                                     UserMapper userMapper,
                                     ConnectionLimitInterceptor connectionLimitInterceptor) {
        this.sessionContextManager = sessionContextManager;
        this.roomStateManager = roomStateManager;
        this.roomSummaryManager = roomSummaryManager;
        this.broadcastService = broadcastService;
        this.onlineService = onlineService;
        this.userMapper = userMapper;
        this.connectionLimitInterceptor = connectionLimitInterceptor;
    }

    @PreDestroy
    public void shutdownCleanupExecutor() {
        cleanupExecutor.shutdownNow();
    }

    @Override
    public void registerSession(WebSocketSession session, String userCode, String pageType) {
        if (session == null || userCode == null || userCode.isBlank()) {
            return;
        }

        // 判断是否为页面刷新：如果 userGroupChatJoinTime 中已有记录，
        // 说明旧会话的延迟清理尚未执行（或新连接抢在旧 afterConnectionClosed 之前到达），
        // 此时保留原有的 joinTime，确保刷新后群聊消息不丢失。
        Long existingJoinTime = sessionContextManager.getUserGroupChatJoinTime().get(userCode);
        long groupChatJoinTime = existingJoinTime != null ? existingJoinTime : System.currentTimeMillis();
        sessionContextManager.getUserGroupChatJoinTime().put(userCode, groupChatJoinTime);

        cancelPendingCleanup(userCode);

        // 先创建 SessionContextDTO 并放入 map，确保映射存在时上下文一定可用
        SessionContextDTO sessionContextDTO = new SessionContextDTO();
        sessionContextDTO.setUserCode(userCode);
        sessionContextDTO.setNickname(null);
        WebSocketSession guardedSession = new ConcurrentWebSocketSessionDecorator(
            session,
            WS_SEND_TIME_LIMIT_MS,
            WS_BUFFER_SIZE_LIMIT_BYTES
        );
        sessionContextDTO.setSession(guardedSession);
        sessionContextDTO.setPageType(pageType);
        sessionContextDTO.setLastHeartbeat(System.currentTimeMillis());
        sessionContextDTO.setEffective(true);
        sessionContextDTO.setHeartbeatTimeout(60000L);
        sessionContextDTO.setGroupChatJoinTime(groupChatJoinTime);
        sessionContextDTO.setRoomCode(roomStateManager.getRoomCodeByUserCode(userCode) == null ? null : String.valueOf(roomStateManager.getRoomCodeByUserCode(userCode)));
        // 记录客户端 IP，用于连接限流
        try {
            if (session.getRemoteAddress() != null) {
                sessionContextDTO.setIp(session.getRemoteAddress().getAddress().getHostAddress());
            }
        } catch (Exception ignored) {
        }
        sessionContextManager.getSessionContextMap().put(session.getId(), sessionContextDTO);

        String previousSessionId = sessionContextManager.getUserCodeToSessionIdMap().put(userCode, session.getId());
        if (previousSessionId != null && !previousSessionId.equals(session.getId())) {
            sessionContextManager.getSessionContextMap().remove(previousSessionId);
        }

        // DB 查询 nickname 在上下文已就绪后进行（best-effort，失败不影响功能）
        try {
            User user = userMapper.selectOne(new QueryWrapper<User>().eq("user_code", userCode));
            if (user != null) {
                sessionContextDTO.setNickname(user.getDisplayName());
            }
        } catch (Exception e) {
            log.warn("Failed to load nickname for userCode={}: {}", userCode, e.getMessage());
        }

        sessionContextManager.indexSession(userCode, pageType);
        sessionContextManager.fitStatus(session.getId());
        sessionContextManager.logSessionContextMap("registerSession userCode=" + userCode + ", pageType=" + pageType);
        if ("home".equals(pageType)) {
            broadcastService.sendHomeFriendStatuses(userCode);
            // 注册到首页时自动推送群聊历史，避免前端刷新时 WS 尚未 OPEN 导致 requestGroupChatHistory 丢失
            broadcastService.sendGroupChatHistory(userCode);
        }
        broadcastService.broadcastFriendStatusChange(userCode);
    }

    @Override
    public void updateHeartbeat(String sessionId) {
        SessionContextDTO sessionContextDTO = sessionContextManager.getSessionContextMap().get(sessionId);
        if (sessionContextDTO == null) {
            return;
        }
        sessionContextDTO.setLastHeartbeat(System.currentTimeMillis());
    }

    @Override
    public SessionContextDTO getSessionContext(String sessionId) {
        return sessionContextManager.getSessionContextMap().get(sessionId);
    }

    @Override
    public void removeSession(String sessionId) {
        SessionContextDTO sessionContextDTO = sessionContextManager.getSessionContextMap().remove(sessionId);
        if (sessionContextDTO == null) {
            return;
        }

        String userCode = sessionContextDTO.getUserCode();
        String pageType = sessionContextDTO.getPageType();
        if (userCode != null) {
            sessionContextManager.unindexSession(userCode, pageType);
            // 清理 IP 连接计数
            if (sessionContextDTO.getIp() != null) {
                connectionLimitInterceptor.decrementIp(sessionContextDTO.getIp());
            }
            String currentSessionId = sessionContextManager.getUserCodeToSessionIdMap().get(userCode);
            if (sessionId.equals(currentSessionId)) {
                sessionContextManager.getUserCodeToSessionIdMap().remove(userCode);
            }
            scheduleDeferredCleanup(userCode, sessionId, pageType);
        }
    }

    private void scheduleDeferredCleanup(String userCode, String sessionId, String pageType) {
        cancelPendingCleanup(userCode);

        ScheduledFuture<?> future = cleanupExecutor.schedule(() -> {
            try {
                String currentSessionId = sessionContextManager.getUserCodeToSessionIdMap().get(userCode);
                if (currentSessionId != null && !sessionId.equals(currentSessionId)) {
                    return;
                }

                // 真正断线（非刷新），清除群聊上线时间，下次登录只看到新消息
                sessionContextManager.getUserGroupChatJoinTime().remove(userCode);

                broadcastService.broadcastFriendStatusOffline(userCode);

                Integer roomCode = roomStateManager.getRoomCodeByUserCode(userCode);
                if (roomCode != null && isRoomPage(pageType)) {
                    if ("online".equals(pageType)) {
                        onlineService.leaveRoom(userCode);
                    } else {
                        roomStateManager.removeUserFromRoom(userCode);
                        roomSummaryManager.applyMemberChange(roomCode, userCode, false);
                        RoomState roomState = roomStateManager.getRoomStates().get(roomCode);
                        if (roomState != null) {
                            broadcastService.broadcastRoomLobbyState(roomCode);
                        }
                    }
                }
            } finally {
                pendingCleanupTasks.remove(userCode);
            }
        }, DISCONNECT_GRACE_PERIOD_MS, TimeUnit.MILLISECONDS);

        pendingCleanupTasks.put(userCode, future);
    }

    private void cancelPendingCleanup(String userCode) {
        ScheduledFuture<?> future = pendingCleanupTasks.remove(userCode);
        if (future != null) {
            future.cancel(false);
        }
    }

    private boolean isRoomPage(String pageType) {
        return "prepare".equals(pageType) || "online".equals(pageType);
    }

}