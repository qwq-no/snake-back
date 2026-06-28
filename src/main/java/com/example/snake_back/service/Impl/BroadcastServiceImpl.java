package com.example.snake_back.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.snake_back.manager.GroupChatManager;
import com.example.snake_back.manager.RoomStateManager;
import com.example.snake_back.manager.RoomSummaryManager;
import com.example.snake_back.manager.SessionContextManager;
import com.example.snake_back.mapper.UserMapper;
import com.example.snake_back.pojo.dto.*;
import com.example.snake_back.pojo.entity.User;
import com.example.snake_back.pojo.vo.*;
import com.example.snake_back.service.BroadcastService;
import com.example.snake_back.service.FriendshipService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class BroadcastServiceImpl implements BroadcastService {

    private final RoomStateManager roomStateManager;
    private final RoomSummaryManager roomSummaryManager;
    private final ObjectMapper objectMapper;
    private final SessionContextManager sessionContextManager;
    private final FriendshipService friendshipService;
    private final UserMapper userMapper;
    private final ExecutorService roomStateBroadcastExecutor = new ThreadPoolExecutor(
        4, 8, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(2048),
        Executors.defaultThreadFactory(),
        new ThreadPoolExecutor.CallerRunsPolicy()
    );
    private final Map<Integer, RoomSnapshotVO> lastRoomSnapshots = new ConcurrentHashMap<>();
    private final AtomicLong onlineDeltaBroadcastCounter = new AtomicLong(0);
    private final AtomicLong onlineSnapshotBroadcastCounter = new AtomicLong(0);
    private final AtomicLong onlineDebugTimeBroadcastCounter = new AtomicLong(0);
    private final AtomicLong onlineDeltaSendCounter = new AtomicLong(0);
    private final AtomicLong onlineSnapshotSendCounter = new AtomicLong(0);
    private final AtomicLong onlineDebugTimeSendCounter = new AtomicLong(0);
    private final AtomicLong onlineDeltaSendEnterCounter = new AtomicLong(0);
    private final AtomicLong onlineSnapshotSendEnterCounter = new AtomicLong(0);
    private final AtomicLong onlineDebugTimeSendEnterCounter = new AtomicLong(0);
    private final Map<String, Integer> sessionSendFailureStreak = new ConcurrentHashMap<>();
    private final Map<String, Integer> sessionSlowSendStreak = new ConcurrentHashMap<>();
    private final Map<String, Long> sessionLastSendSuccessAt = new ConcurrentHashMap<>();

    private static final long WS_SLOW_SEND_WARN_MS = 120L;
    private static final int WS_SEND_FAILURE_STREAK_THRESHOLD = 3;
    private static final int WS_SLOW_SEND_STREAK_WARN_THRESHOLD = 5;
    private static final long WS_STALE_SEND_CLOSE_MS = 15000L;

    private final GroupChatManager groupChatManager;

    public BroadcastServiceImpl(RoomStateManager roomStateManager, RoomSummaryManager roomSummaryManager, ObjectMapper objectMapper,
                                SessionContextManager sessionContextManager, FriendshipService friendshipService, UserMapper userMapper,
                                GroupChatManager groupChatManager) {
        this.roomStateManager = roomStateManager;
        this.roomSummaryManager = roomSummaryManager;
        this.objectMapper = objectMapper;
        this.sessionContextManager = sessionContextManager;
        this.friendshipService = friendshipService;
        this.userMapper = userMapper;
        this.groupChatManager = groupChatManager;
    }

    @PreDestroy
    public void shutdownBroadcastExecutor() {
        roomStateBroadcastExecutor.shutdownNow();
    }

    @Override
    public void broadcastRoomSummaries(RoomSummaryVO[] roomSummaries) {
        if (roomSummaries == null) {
            return;
        }
        WsResponse<RoomSummaryVO[]> response = new WsResponse<>();
        response.setPageType("select");
        response.setType("room_summary_list");
        response.setData(roomSummaries);
        sendToPage("select", toJson(response));
    }

    @Override
    public void sendHomeFriendStatuses(String userCode) {
        Integer myUserCode = parseUserCode(userCode);
        if (myUserCode == null) {
            return;
        }

        List<FriendListVO> snapshot = buildHomeFriendSnapshot(myUserCode);

        WsResponse<List<FriendListVO>> response = new WsResponse<>();
        response.setPageType("home");
        response.setType("friend_status_list");
        response.setData(snapshot);
        sendToUser(userCode, toJson(response));
    }

    @Override
    public void broadcastFriendStatusChange(String userCode) {
        Integer changedUserCode = parseUserCode(userCode);
        if (changedUserCode == null) {
            return;
        }

        SessionContextDTO changedContext = getContextByUserCode(userCode);
        if (changedContext == null) {
            return;
        }

        String status = changedContext.getStatus();
        if (status == null) {
            String sessionId = sessionContextManager.getUserCodeToSessionIdMap().get(userCode);
            status = sessionId == null ? null : sessionContextManager.fitStatus(sessionId);
        }

        FriendListVO payload = new FriendListVO();
        payload.setUserCode(changedUserCode);
        payload.setUserName(resolveDisplayName(changedUserCode));
        payload.setStatus(status);
        String friendRoomCode = changedContext.getRoomCode();
        payload.setRoomCode(friendRoomCode != null && !friendRoomCode.isBlank() ? Integer.parseInt(friendRoomCode) : null);

        WsResponse<FriendListVO> response = new WsResponse<>();
        response.setPageType("home");
        response.setType("friend_status_change");
        response.setData(payload);
        sendToHomeFriends(changedUserCode, toJson(response));
    }

    @Override
    public void broadcastFriendStatusOffline(String userCode) {
        Integer changedUserCode = parseUserCode(userCode);
        if (changedUserCode == null) {
            return;
        }

        FriendListVO payload = new FriendListVO();
        payload.setUserCode(changedUserCode);
        payload.setUserName(resolveDisplayName(changedUserCode));
        payload.setStatus(null);
        payload.setRoomCode(null);

        WsResponse<FriendListVO> response = new WsResponse<>();
        response.setPageType("home");
        response.setType("friend_status_offline");
        response.setData(payload);
        sendToHomeFriends(changedUserCode, toJson(response));
    }

    @Override
    public void sendRoomSummaries(String userCode) {
        WsResponse<RoomSummaryVO[]> response = new WsResponse<>();
        response.setPageType("select");
        response.setType("room_summary_list");
        response.setData(roomSummaryManager.getAllRooms());
        sendToUser(userCode, toJson(response));
    }

    @Override
    public void broadcastRoomSummary(RoomSummaryVO roomSummary) {
        if (roomSummary == null) {
            return;
        }
        WsResponse<RoomSummaryVO> response = new WsResponse<>();
        response.setPageType("select");
        response.setType("room_summary_delta");
        response.setData(roomSummary);
        sendToPage("select", toJson(response));
    }

    @Override
    public void broadcastRoomLobbyState(Integer roomCode) {
        Set<String> members = roomStateManager.getRoomMembers().get(roomCode);
        Set<String> readyUsers = roomStateManager.getRoomReadyUsers().get(roomCode);
        RoomState roomState = roomStateManager.getRoomStates().get(roomCode);

        if (members == null || roomState == null) {
            return;
        }

        RoomLobbyStateVO lobbyState = new RoomLobbyStateVO();
        lobbyState.setRoomCode(roomCode);
        lobbyState.setStatus(roomState.getStatus());
        lobbyState.setMembers(new HashSet<>(members));
        lobbyState.setReadyUsers(readyUsers == null ? new HashSet<>() : new HashSet<>(readyUsers));

        WsResponse<RoomLobbyStateVO> response = new WsResponse<>();
        response.setPageType("prepare");
        if(roomState.getStatus().equals("playing")){
            response.setType("ready_over");
        }
        else
            response.setType("ready_state");
        response.setData(lobbyState);

        String json = toJson(response);

        // 一个房间一个任务，内部循环发所有人（与 broadcastRoomSnapshot 一致）
        final String finalLobbyJson = json;
        roomStateBroadcastExecutor.execute(() -> {
            for (String userCode : members) {
                String sessionId = sessionContextManager.getUserCodeToSessionIdMap().get(userCode);
                SessionContextDTO context = sessionId == null ? null : sessionContextManager.getSessionContextMap().get(sessionId);
                WebSocketSession session = context == null ? null : context.getSession();
                if (session == null || !session.isOpen()) continue;
                try {
                    session.sendMessage(new TextMessage(finalLobbyJson));
                } catch (IOException e) {
                }
            }
        });
    }

    @Override
    public void broadcastRoomState(Integer roomCode) {
        RoomState roomState = roomStateManager.getRoomStates().get(roomCode);
        if (roomState == null) {
            return;
        }
        broadcastRoomSnapshot(roomState);
    }

    @Override
    public void broadcastRoomState(RoomState roomState) {
        broadcastRoomSnapshot(roomState);
    }

    @Override
    public void broadcastRoomDelta(RoomState roomState) {
        if (roomState == null) return;

        Integer roomCode = roomState.getRoomCode();
        Set<String> members = roomStateManager.getRoomMembers().get(roomCode);
        if (members == null || members.isEmpty()) return;

        long broadcastAt = System.currentTimeMillis();

        RoomSnapshotVO currentSnapshot = toRoomSnapshot(roomState);
        RoomSnapshotVO previousSnapshot = lastRoomSnapshots.get(roomCode);
        if (previousSnapshot == null) {
            // 全量快照已通过 initGame()/joinRoom()/pushSnapshot() 按需发送，
            // 这里只缓存供后续 delta 计算，避免对全房间成员重复广播
            lastRoomSnapshots.put(roomCode, currentSnapshot);
            return;
        }

        RoomDeltaVO delta = toRoomDelta(previousSnapshot, currentSnapshot);
        if (delta == null) {
            // 即使没有变化也发送一个空 delta（仅 serverTime），让前端知道连接仍然存活
            RoomDeltaVO keepAliveDelta = new RoomDeltaVO();
            keepAliveDelta.setServerTime(System.currentTimeMillis());
            String keepAliveJson = toJson(new WsResponse<RoomDeltaVO>() {{
                setPageType("online");
                setType("room_delta");
                setData(keepAliveDelta);
            }});
            lastRoomSnapshots.put(roomCode, currentSnapshot);
            // 一个房间一个任务，内部循环发所有人，避免每 tick 1000+ 个 execute 撑爆队列
            final String finalKeepAliveJson = keepAliveJson;
            roomStateBroadcastExecutor.execute(() -> {
                for (String userCode : members) {
                    String sessionId = sessionContextManager.getUserCodeToSessionIdMap().get(userCode);
                    SessionContextDTO context = sessionId == null ? null : sessionContextManager.getSessionContextMap().get(sessionId);
                    WebSocketSession session = context == null ? null : context.getSession();
                    if (session == null || !session.isOpen()) continue;
                    sendOnlineMessage(session, userCode, "delta", roomCode, finalKeepAliveJson);
                }
            });
            return;
        }

        WsResponse<RoomDeltaVO> response = new WsResponse<>();
        response.setPageType("online");
        response.setType("room_delta");
        response.setData(delta);

        String json = toJson(response);
        long deltaSeq = onlineDeltaBroadcastCounter.incrementAndGet();
        if (deltaSeq % 3 == 0) {
        }

        lastRoomSnapshots.put(roomCode, currentSnapshot);

        // 一个房间一个任务，内部循环发所有人
        final String finalJson = json;
        roomStateBroadcastExecutor.execute(() -> {
            for (String userCode : members) {
                String sessionId = sessionContextManager.getUserCodeToSessionIdMap().get(userCode);
                SessionContextDTO context = sessionId == null ? null : sessionContextManager.getSessionContextMap().get(sessionId);
                WebSocketSession session = context == null ? null : context.getSession();
                if (session == null || !session.isOpen()) continue;
                if (closeStaleSessionIfNeeded(session)) continue;
                sendOnlineMessage(session, userCode, "delta", roomCode, finalJson);
            }
        });
    }

    private void broadcastRoomSnapshot(RoomState roomState) {
        if (roomState == null) return;

        Integer roomCode = roomState.getRoomCode();
        Set<String> members = roomStateManager.getRoomMembers().get(roomCode);
        if (members == null || members.isEmpty()) return;

        long broadcastAt = System.currentTimeMillis();

        RoomSnapshotVO snapshot = toRoomSnapshot(roomState);

        WsResponse<RoomSnapshotVO> response = new WsResponse<>();
        response.setPageType("online");
        response.setType("room_snapshot");
        response.setData(snapshot);

        String json = toJson(response);
        long snapshotSeq = onlineSnapshotBroadcastCounter.incrementAndGet();
        if (snapshotSeq % 3 == 0) {
        }

        lastRoomSnapshots.put(roomCode, snapshot);

        // 一个房间一个任务，内部循环发所有人
        final String finalSnapshotJson = json;
        roomStateBroadcastExecutor.execute(() -> {
            for (String userCode : members) {
                String sessionId = sessionContextManager.getUserCodeToSessionIdMap().get(userCode);
                SessionContextDTO context = sessionId == null ? null : sessionContextManager.getSessionContextMap().get(sessionId);
                WebSocketSession session = context == null ? null : context.getSession();
                if (session == null || !session.isOpen()) continue;
                if (closeStaleSessionIfNeeded(session)) continue;
                sendOnlineMessage(session, userCode, "snapshot", roomCode, finalSnapshotJson);
            }
        });
    }

    @Override
    public void broadcastRoomDebugTime(Integer roomCode, long timestamp) {
        Set<String> members = roomStateManager.getRoomMembers().get(roomCode);
        if (members == null || members.isEmpty()) {
            return;
        }


        WsResponse<Long> response = new WsResponse<>();
        response.setPageType("online");
        response.setType("room_debug_time");
        response.setData(timestamp);

        String json = toJson(response);
        long debugSeq = onlineDebugTimeBroadcastCounter.incrementAndGet();
        if (debugSeq % 3 == 0) {
        }
        for (String userCode : members) {
            String sessionId = sessionContextManager.getUserCodeToSessionIdMap().get(userCode);
            SessionContextDTO context = sessionId == null ? null : sessionContextManager.getSessionContextMap().get(sessionId);
            WebSocketSession session = context == null ? null : context.getSession();
            if (session == null || !session.isOpen()) {
                continue;
            }
            if (closeStaleSessionIfNeeded(session)) {
                continue;
            }
            String finalJson = json;
            roomStateBroadcastExecutor.execute(() ->
                sendOnlineMessage(session, userCode, "debug_time", roomCode, finalJson)
            );
        }
    }

    private void sendOnlineMessage(WebSocketSession session, String userCode, String channel, Integer roomCode, String json) {
        if (session == null || !session.isOpen()) return;
        try {
            session.sendMessage(new TextMessage(json));
        } catch (Exception ex) {
        }
    }

    private boolean closeStaleSessionIfNeeded(WebSocketSession session) {
        String sessionId = session.getId();
        long now = System.currentTimeMillis();
        long lastSuccessAt = sessionLastSendSuccessAt.getOrDefault(sessionId, now);
        if (now - lastSuccessAt < WS_STALE_SEND_CLOSE_MS) {
            return false;
        }

        // 如果一个房间里只有当前这一个成员，不要关闭 session，否则房间会变为空房间导致 tick 停止
        Integer roomCode = lookupRoomCodeForSession(sessionId);
        if (roomCode != null) {
            Set<String> members = roomStateManager.getRoomMembers().get(roomCode);
            if (members != null && members.size() <= 1) {
                // 单用户房间不执行 stale close，避免误杀
                sessionLastSendSuccessAt.put(sessionId, System.currentTimeMillis());
                return false;
            }
        }

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                session.close(CloseStatus.SESSION_NOT_RELIABLE);
            } catch (Exception e) {
            }
        });
        sessionLastSendSuccessAt.remove(sessionId);
        sessionSlowSendStreak.remove(sessionId);
        sessionSendFailureStreak.remove(sessionId);
        return true;
    }

    private void logOnlineSendTag(String channel) {
        if ("delta".equals(channel)) {
            long seq = onlineDeltaSendCounter.incrementAndGet();
            if (seq % 3 == 0) {
            }
            return;
        }
        if ("snapshot".equals(channel)) {
            long seq = onlineSnapshotSendCounter.incrementAndGet();
            if (seq % 3 == 0) {
            }
            return;
        }
        if ("debug_time".equals(channel)) {
            long seq = onlineDebugTimeSendCounter.incrementAndGet();
            if (seq % 3 == 0) {
            }
        }
    }

    private void logOnlineSendEnterTag(String channel) {
        if ("delta".equals(channel)) {
            long seq = onlineDeltaSendEnterCounter.incrementAndGet();
            if (seq % 3 == 0) {
            }
            return;
        }
        if ("snapshot".equals(channel)) {
            long seq = onlineSnapshotSendEnterCounter.incrementAndGet();
            if (seq % 3 == 0) {
            }
            return;
        }
        if ("debug_time".equals(channel)) {
            long seq = onlineDebugTimeSendEnterCounter.incrementAndGet();
            if (seq % 3 == 0) {
            }
        }
    }

    private List<FriendListVO> buildHomeFriendSnapshot(Integer myUserCode) {
        List<FriendListVO> result = new ArrayList<>();
        List<FriendListVO> friends = friendshipService.getFriendList(myUserCode);
        if (friends == null) {
            return result;
        }

        for (FriendListVO friend : friends) {
            SessionContextDTO context = getContextByUserCode(String.valueOf(friend.getUserCode()));
            FriendListVO vo = new FriendListVO();
            vo.setUserCode(friend.getUserCode());
            vo.setUserName(friend.getUserName());
            if (context == null) {
                vo.setStatus(null);
                vo.setRoomCode(null);
            } else {
                if (context.getStatus() != null) {
                    vo.setStatus(context.getStatus());
                } else {
                    String sessionId = resolveSessionId(String.valueOf(friend.getUserCode()));
                    vo.setStatus(sessionId == null ? null : sessionContextManager.fitStatus(sessionId));
                }
                // 好友在房间中（online 或 prepare）时传递房间号
                String friendRoomCode = context.getRoomCode();
                vo.setRoomCode(friendRoomCode != null && !friendRoomCode.isBlank() ? Integer.parseInt(friendRoomCode) : null);
            }
            result.add(vo);
        }

        return result;
    }

    private RoomSnapshotVO toRoomSnapshot(RoomState roomState) {
        RoomSnapshotVO snapshot = new RoomSnapshotVO();
        synchronized (roomState) {
            snapshot.setRoomCode(roomState.getRoomCode());
            snapshot.setCountdownMin(roomState.getCountdownMin());
            snapshot.setCountdownSecond(roomState.getCountdownSecond());
            snapshot.setGameStartTime(roomState.getGameStartTime());
            snapshot.setStatus(roomState.getStatus());
            snapshot.setMap(copyMap(roomState.getMap()));
            snapshot.setSnakes(roomState.getSnakes().stream().map(this::toSnakeSnapshot).toList());
            snapshot.setFruits(roomState.getFruits() == null ? new HashSet<>() : new HashSet<>(roomState.getFruits()));
            snapshot.setSpeedUp(roomState.getSpeedUp() == null ? new HashSet<>() : new HashSet<>(roomState.getSpeedUp()));
            snapshot.setSpeedDown(roomState.getSpeedDown() == null ? new HashSet<>() : new HashSet<>(roomState.getSpeedDown()));
            snapshot.setRevealAll(roomState.getRevealAll() == null ? new HashSet<>() : new HashSet<>(roomState.getRevealAll()));
            snapshot.setFog(roomState.getFog() == null ? new HashSet<>() : new HashSet<>(roomState.getFog()));
            snapshot.setRoomEmojis(roomState.getRoomEmojis() == null ? new ArrayDeque<>() : new ArrayDeque<>(roomState.getRoomEmojis()));
        }
        return snapshot;
    }

    private RoomDeltaVO toRoomDelta(RoomSnapshotVO previousSnapshot, RoomSnapshotVO currentSnapshot) {
        if (previousSnapshot == null || currentSnapshot == null) {
            return null;
        }

        RoomDeltaVO delta = new RoomDeltaVO();
        delta.setServerTime(System.currentTimeMillis());

        if (!Objects.equals(previousSnapshot.getStatus(), currentSnapshot.getStatus())) {
            delta.setStatus(currentSnapshot.getStatus());
        }

        delta.setSnakeDeltas(buildSnakeDeltas(previousSnapshot.getSnakes(), currentSnapshot.getSnakes()));
        delta.setFruitAdded(diffPoints(currentSnapshot.getFruits(), previousSnapshot.getFruits()));
        delta.setFruitRemoved(diffPoints(previousSnapshot.getFruits(), currentSnapshot.getFruits()));
        delta.setSpeedUpAdded(diffPoints(currentSnapshot.getSpeedUp(), previousSnapshot.getSpeedUp()));
        delta.setSpeedUpRemoved(diffPoints(previousSnapshot.getSpeedUp(), currentSnapshot.getSpeedUp()));
        delta.setSpeedDownAdded(diffPoints(currentSnapshot.getSpeedDown(), previousSnapshot.getSpeedDown()));
        delta.setSpeedDownRemoved(diffPoints(previousSnapshot.getSpeedDown(), currentSnapshot.getSpeedDown()));
        delta.setRevealAllAdded(diffPoints(currentSnapshot.getRevealAll(), previousSnapshot.getRevealAll()));
        delta.setRevealAllRemoved(diffPoints(previousSnapshot.getRevealAll(), currentSnapshot.getRevealAll()));
        delta.setFogAdded(diffPoints(currentSnapshot.getFog(), previousSnapshot.getFog()));
        delta.setFogRemoved(diffPoints(previousSnapshot.getFog(), currentSnapshot.getFog()));

        if (!Objects.equals(previousSnapshot.getRoomEmojis(), currentSnapshot.getRoomEmojis())) {
            delta.setRoomEmojis(copyEmojis(currentSnapshot.getRoomEmojis()));
        }

        // detect whether remaining seconds (countdown) changed between snapshots
        long prevRemaining = previousSnapshot.getCountdownMin() * 60 + previousSnapshot.getCountdownSecond();
        long currRemaining = currentSnapshot.getCountdownMin() * 60 + currentSnapshot.getCountdownSecond();
        boolean countdownChanged = prevRemaining != currRemaining;
        if (countdownChanged) {
            delta.setRemainingSeconds(currRemaining);
        }

        if (delta.getStatus() == null
            && !countdownChanged
            && isEmpty(delta.getSnakeDeltas())
                && isEmpty(delta.getFruitAdded())
                && isEmpty(delta.getFruitRemoved())
                && isEmpty(delta.getSpeedUpAdded())
                && isEmpty(delta.getSpeedUpRemoved())
                && isEmpty(delta.getSpeedDownAdded())
                && isEmpty(delta.getSpeedDownRemoved())
                && isEmpty(delta.getRevealAllAdded())
                && isEmpty(delta.getRevealAllRemoved())
                && isEmpty(delta.getFogAdded())
                && isEmpty(delta.getFogRemoved())
                && isEmpty(delta.getRoomEmojis())) {
            return null;
        }

        return delta;
    }

    private List<SnakeDeltaVO> buildSnakeDeltas(List<SnakeSnapshotVO> previousSnakes, List<SnakeSnapshotVO> currentSnakes) {
        List<SnakeDeltaVO> result = new ArrayList<>();
        int previousSize = previousSnakes == null ? 0 : previousSnakes.size();
        int currentSize = currentSnakes == null ? 0 : currentSnakes.size();
        int maxSize = Math.max(previousSize, currentSize);

        for (int i = 0; i < maxSize; i++) {
            SnakeSnapshotVO previous = i < previousSize ? previousSnakes.get(i) : null;
            SnakeSnapshotVO current = i < currentSize ? currentSnakes.get(i) : null;
            SnakeDeltaVO delta = buildSnakeDelta(previous, current);
            if (delta != null) {
                delta.setSnakeIndex(i);
                result.add(delta);
            }
        }

        return result;
    }

    private SnakeDeltaVO buildSnakeDelta(SnakeSnapshotVO previous, SnakeSnapshotVO current) {
        if (previous == null && current == null) {
            return null;
        }

        SnakeDeltaVO delta = new SnakeDeltaVO();
        if (current == null) {
            delta.setDeltaType(SnakeDeltaVO.TYPE_RELEASE);
            delta.setOwnerUserCode(null);
            delta.setAlive(false);
            return delta;
        }

        delta.setOwnerUserCode(current.getOwnerUserCode());
        delta.setAlive(current.isAlive());

        if (previous == null) {
            delta.setDeltaType(SnakeDeltaVO.TYPE_SYNC);
            fillSnakeGeometry(delta, current, true);
            return delta;
        }

        if (!Objects.equals(previous.getOwnerUserCode(), current.getOwnerUserCode())) {
            if (previous.getOwnerUserCode() == null && current.getOwnerUserCode() != null) {
                delta.setDeltaType(SnakeDeltaVO.TYPE_TAKEOVER);
            } else if (previous.getOwnerUserCode() != null && current.getOwnerUserCode() == null) {
                delta.setDeltaType(SnakeDeltaVO.TYPE_RELEASE);
            } else {
                delta.setDeltaType(SnakeDeltaVO.TYPE_SYNC);
            }
            fillSnakeGeometry(delta, current, true);
            return delta;
        }

        if (previous.isAlive() != current.isAlive()) {
            delta.setDeltaType(current.isAlive() ? SnakeDeltaVO.TYPE_RESPAWN : SnakeDeltaVO.TYPE_DIE);
            if (!current.isAlive()) {
                delta.setDeathReason(current.getDeathReason());
            }
            fillSnakeGeometry(delta, current, true);
            return delta;
        }

        if (!Objects.equals(previous.getBody(), current.getBody())
                || previous.getEmojiTimer() != current.getEmojiTimer()
                || previous.getRevealAllTimer() != current.getRevealAllTimer()
                || previous.getFogTimer() != current.getFogTimer()
                || previous.isAlive() != current.isAlive()
                || !Objects.equals(previous.getMaxLength(), current.getMaxLength())) {
            delta.setDeltaType(SnakeDeltaVO.TYPE_MOVE);
            fillSnakeGeometry(delta, current, false);
            return delta;
        }

        return null;
    }

    private void fillSnakeGeometry(SnakeDeltaVO delta, SnakeSnapshotVO snapshot, boolean includeBody) {
        if (snapshot.getBody() != null && !snapshot.getBody().isEmpty()) {
            delta.setHead(snapshot.getBody().get(0));
        }
        if (includeBody) {
            delta.setBody(snapshot.getBody());
        }
        delta.setRevealAllTimer(snapshot.getRevealAllTimer());
        delta.setFogTimer(snapshot.getFogTimer());
        delta.setMaxLength(snapshot.getMaxLength());
        if (snapshot.getMaxLength() != null && snapshot.getMaxLength() > 0) {
        }
    }

    private List<PointVO> diffPoints(Set<String> current, Set<String> previous) {
        if (current == null || current.isEmpty()) {
            return List.of();
        }
        List<PointVO> result = new ArrayList<>();
        for (String value : current) {
            if (previous != null && previous.contains(value)) {
                continue;
            }
            PointVO pointVO = parsePoint(value);
            if (pointVO != null) {
                result.add(pointVO);
            }
        }
        return result;
    }

    private List<EmojiMessageVO> copyEmojis(Deque<EmojiMessageVO> roomEmojis) {
        if (roomEmojis == null || roomEmojis.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(roomEmojis);
    }

    private PointVO parsePoint(String value) {
        if (value == null || !value.contains(",")) {
            return null;
        }
        String[] parts = value.split(",");
        if (parts.length != 2) {
            return null;
        }
        try {
            PointVO pointVO = new PointVO();
            pointVO.setX(Integer.parseInt(parts[0].trim()));
            pointVO.setY(Integer.parseInt(parts[1].trim()));
            return pointVO;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }

    private SnakeSnapshotVO toSnakeSnapshot(SnakeState snakeState) {
        SnakeSnapshotVO snapshot = new SnakeSnapshotVO();
        snapshot.setBody(toPointList(snakeState.getBody()));
        snapshot.setAlive(snakeState.isAlive());
        snapshot.setEmojiTimer(snakeState.getEmojiTimer());
        if (snakeState.getPropsTimer() != null) {
            snapshot.setRevealAllTimer(snakeState.getPropsTimer().getRevealAll());
            snapshot.setFogTimer(snakeState.getPropsTimer().getFog());
        }
        snapshot.setOwnerUserCode(snakeState.getOwnerUserCode());
        snapshot.setMaxLength(snakeState.getMaxLength());
        snapshot.setDeathReason(snakeState.getDeathReason());
        snapshot.setType(snakeState.getType());
        return snapshot;
    }

    private List<PointVO> toPointList(List<Node> body) {
        if (body == null || body.isEmpty()) {
            return List.of();
        }
        return body.stream().map(this::toPointVO).toList();
    }

    private PointVO toPointVO(Node node) {
        PointVO pointVO = new PointVO();
        pointVO.setX(node.getX());
        pointVO.setY(node.getY());
        return pointVO;
    }

    private int[][] copyMap(int[][] source) {
        if (source == null) {
            return null;
        }
        int[][] copy = new int[source.length][];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] == null ? null : Arrays.copyOf(source[i], source[i].length);
        }
        return copy;
    }

    private void sendToHomeFriends(Integer changedUserCode, String json) {
        List<FriendListVO> friends = friendshipService.getFriendList(changedUserCode);
        if (friends == null || friends.isEmpty()) {
            return;
        }

        for (FriendListVO friend : friends) {
            SessionContextDTO friendContext = getContextByUserCode(String.valueOf(friend.getUserCode()));
            if (friendContext == null || friendContext.getPageType() == null || !"home".equals(friendContext.getPageType())) {
                continue;
            }
            WebSocketSession session = friendContext.getSession();
            if (session == null || !session.isOpen()) {
                continue;
            }
            try {
                session.sendMessage(new TextMessage(json));
            } catch (IOException e) {
            }
        }
    }

    private SessionContextDTO getContextByUserCode(String userCode) {
        String sessionId = resolveSessionId(userCode);
        if (sessionId == null) {
            return null;
        }
        return sessionContextManager.getSessionContextMap().get(sessionId);
    }

    private String resolveSessionId(String userCode) {
        if (userCode == null || userCode.isBlank()) {
            return null;
        }

        String normalizedUserCode = normalizeUserCode(userCode);
        String sessionId = sessionContextManager.getUserCodeToSessionIdMap().get(normalizedUserCode);
        if (sessionId != null) {
            return sessionId;
        }

        if (!normalizedUserCode.equals(userCode)) {
            return sessionContextManager.getUserCodeToSessionIdMap().get(userCode);
        }

        return null;
    }

    private String normalizeUserCode(String userCode) {
        String trimmed = userCode.trim();
        if (!trimmed.matches("\\d+")) {
            return trimmed;
        }

        try {
            return String.format("%06d", Integer.parseInt(trimmed));
        } catch (NumberFormatException e) {
            return trimmed;
        }
    }

    private Integer parseUserCode(String userCode) {
        try {
            return userCode == null ? null : Integer.parseInt(userCode);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String resolveDisplayName(Integer userCode) {
        if (userCode == null) {
            return null;
        }
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("user_code", userCode));
        return user == null ? null : user.getDisplayName();
    }

    private void sendToUser(String userCode, String json) {
        SessionContextDTO context = getContextByUserCode(userCode);
        if (context == null || context.getSession() == null || !context.getSession().isOpen()) {
            return;
        }
        try {
            context.getSession().sendMessage(new TextMessage(json));
        } catch (IOException e) {
            throw new RuntimeException("Failed to send ws message to user: " + userCode, e);
        }
    }

    private void sendToPage(String pageType, String json) {
        // 收集目标 session（在调用线程完成），然后一个任务批量发送
        List<WebSocketSession> targets = new ArrayList<>();
        for (Map.Entry<String, SessionContextDTO> entry : sessionContextManager.getSessionContextMap().entrySet()) {
            SessionContextDTO context = entry.getValue();
            if (context == null || context.getPageType() == null || !pageType.equals(context.getPageType())) {
                continue;
            }
            WebSocketSession session = context.getSession();
            if (session != null) {
                targets.add(session);
            }
        }
        if (targets.isEmpty()) return;

        final String finalJson = json;
        roomStateBroadcastExecutor.execute(() -> {
            for (WebSocketSession session : targets) {
                if (!session.isOpen()) continue;
                try {
                    session.sendMessage(new TextMessage(finalJson));
                } catch (IOException e) {
                }
            }
        });
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize ws payload", e);
        }
    }

    private Integer lookupRoomCodeForSession(String sessionId) {
        for (Map.Entry<Integer, Set<String>> entry : roomStateManager.getRoomMembers().entrySet()) {
            for (String memberUserCode : entry.getValue()) {
                String sid = sessionContextManager.getUserCodeToSessionIdMap().get(memberUserCode);
                if (sessionId.equals(sid)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    @Override
    public void broadcastGroupChatMessage(GroupChatMessageVO msg) {
        // 全局群聊 — 只推送给 home 页面用户（O(1) 索引查找，避免遍历所有 session）
        WsResponse<GroupChatMessageVO> homeResponse = new WsResponse<>();
        homeResponse.setPageType("home");
        homeResponse.setType("group_chat_message");
        homeResponse.setData(msg);

        String homeJson = toJson(homeResponse);

        Set<String> homeUsers = sessionContextManager.getUserCodesByPageType("home");
        for (String userCode : homeUsers) {
            String sessionId = sessionContextManager.getUserCodeToSessionIdMap().get(userCode);
            if (sessionId == null) continue;
            SessionContextDTO context = sessionContextManager.getSessionContextMap().get(sessionId);
            if (context == null) continue;
            WebSocketSession session = context.getSession();
            if (session != null && session.isOpen()) {
                sendGroupChatToSession(session, homeJson);
            }
        }
    }

    @Override
    public void broadcastRoomChat(Integer roomCode, GroupChatMessageVO msg) {
        // 房间聊天 — 只推送给同房间的 prepare 页面玩家，不持久化，不用 executor
        Set<String> roomMembers = roomStateManager.getRoomMembers().get(roomCode);
        if (roomMembers == null || roomMembers.isEmpty()) {
            return;
        }

        WsResponse<GroupChatMessageVO> response = new WsResponse<>();
        response.setPageType("prepare");
        response.setType("group_chat_message");
        response.setData(msg);
        String json = toJson(response);

        for (String userCode : roomMembers) {
            SessionContextDTO context = getContextByUserCode(userCode);
            if (context == null || !"prepare".equals(context.getPageType())) {
                continue;
            }
            WebSocketSession session = context.getSession();
            if (session != null && session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(json));
                } catch (IOException ignored) {
                }
            }
        }
    }

    @Override
    public void sendGroupChatHistory(String userCode) {
        // 根据用户的上线时间过滤，只返回上线后的消息（刷新时 joinTime 不变，消息不丢失）
        Long joinTime = sessionContextManager.getUserGroupChatJoinTime().get(userCode);
        long since = joinTime != null ? joinTime : System.currentTimeMillis();
        List<GroupChatMessageVO> messages = groupChatManager.getMessagesSince(since);

        WsResponse<List<GroupChatMessageVO>> response = new WsResponse<>();
        response.setPageType("home");
        response.setType("group_chat_history");
        response.setData(messages);

        sendToUser(userCode, toJson(response));
    }

    private void sendGroupChatToSession(WebSocketSession session, String json) {
        // 群聊小文本直接用调用线程发（sendMessage 是异步写 TCP buffer），
        // 不经 executor 避免高并发时的 submit 开销
        if (!session.isOpen()) return;
        try {
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
        }
    }

    @Override
    public int getBroadcastQueueSize() {
        if (roomStateBroadcastExecutor instanceof ThreadPoolExecutor tpe) {
            return tpe.getQueue().size();
        }
        return -1;
    }
}
