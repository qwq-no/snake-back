package com.example.snake_back.service.Impl;

import com.example.snake_back.common.utils.PageUtil;
import com.example.snake_back.common.utils.RoomUtil;
import com.example.snake_back.manager.RoomStateManager;
import com.example.snake_back.manager.RoomSummaryManager;
import com.example.snake_back.manager.SessionContextManager;
import com.example.snake_back.mapper.UserMapper;
import com.example.snake_back.pojo.dto.Node;
import com.example.snake_back.pojo.dto.RoomState;
import com.example.snake_back.pojo.dto.SnakeState;
import com.example.snake_back.pojo.vo.EmojiMessageVO;
import com.example.snake_back.pojo.vo.RoomSummaryVO;
import com.example.snake_back.service.BroadcastService;
import com.example.snake_back.service.OnlineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class OnlineServiceImpl implements OnlineService {
    private final UserMapper userMapper;
    private static final int MAP_SIZE = 102;
    private final RoomStateManager roomStateManager;
    private final BroadcastService broadcastService;
    private final RoomSummaryManager roomSummaryManager;
    private final PageUtil  pageUtil;
    private final RoomUtil  roomUtil;
    private final SessionContextManager sessionContextManager;

    /** 游戏房间并行处理线程池。每 tick 将各房间提交到此池并行执行，tick 等待所有房间完成后返回。 */
    private final ExecutorService gameRoomExecutor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "game-room-worker");
        t.setDaemon(true);
        return t;
    });

    public OnlineServiceImpl(RoomStateManager roomStateManager, BroadcastService broadcastService,RoomSummaryManager roomSummaryManager,
                              PageUtil pageUtil, RoomUtil roomUtil, SessionContextManager sessionContextManager,
                              UserMapper userMapper) {
        this.roomStateManager = roomStateManager;
        this.broadcastService = broadcastService;
        this.roomSummaryManager = roomSummaryManager;
        this.pageUtil = pageUtil;
        this.roomUtil = roomUtil;
        this.sessionContextManager = sessionContextManager;
        this.userMapper = userMapper;
    }

    @Override
    public void leaveRoom(String userCode) {
        String sessionId = sessionContextManager.getUserCodeToSessionIdMap().get(userCode);
        if (sessionId != null) {
            var context = sessionContextManager.getSessionContextMap().get(sessionId);
            if (context != null) {
                context.setHeartbeatTimeout(60000L);
            }
        }

        Integer roomCode = roomStateManager.getRoomCodeByUserCode(userCode);
        if (roomCode == null) {
            return;
        }

        RoomState roomState = roomStateManager.getRoomStates().get(roomCode);
        if (roomState == null) {
            roomStateManager.removeUserFromRoom(userCode);
            return;
        }

        synchronized (roomState) {
            Integer snakeIndex = roomState.getUserCodeToSnakeIndex().remove(userCode);
            roomStateManager.removeUserFromRoom(userCode);

            RoomSummaryVO updatedSummary = roomSummaryManager.applyMemberChange(roomCode, userCode, false);
            broadcastService.broadcastRoomSummary(updatedSummary);

            if (snakeIndex != null) {
                handlePlayerLeaveSnake(roomState, snakeIndex);
            }

            Set<String> members = roomStateManager.getRoomMembers().get(roomCode);
            if (members == null || members.isEmpty()) {
                roomUtil.resetRoom(roomState);
            } else if ("playing".equals(roomState.getStatus()) || "finished".equals(roomState.getStatus())) {
                broadcastService.broadcastRoomState(roomState);
            }
        }
    }

    @Override
    public void input(String userCode,String key) {
        if(key == null){return;}
        key = key.toLowerCase();
        String newDirection = switch (key) {
            case "w" -> "up";
            case "s" -> "down";
            case "a" -> "left";
            case "d" -> "right";
            default -> null;
        };
        if (newDirection == null) return;
        inputCounter.incrementAndGet();
        Integer roomCode = roomStateManager.getRoomCodeByUserCode(userCode);
        if(roomCode!=null) {
            RoomState roomState = roomStateManager.getOrInitRoom(roomCode);
            Integer snakeIndex = roomState.getUserCodeToSnakeIndex().get(userCode);
            if(snakeIndex != null && snakeIndex >= 0 && snakeIndex < roomState.getSnakes().size()) {
                SnakeState snake = roomState.getSnakes().get(snakeIndex);
                // 方向队列：保留最多 3 个，入队时不检查反向（消费时检查）
                if (snake.getDirectionQueue() == null) {
                    snake.setDirectionQueue(new java.util.ArrayDeque<>());
                }
                java.util.Deque<String> q = snake.getDirectionQueue();
                if (q.size() >= 3) return;
                // 跳过连续相同方向
                String last = q.peekLast();
                if (last != null && last.equals(newDirection)) return;
                q.addLast(newDirection);
            }
        }
    }

    private void enqueueDirection(SnakeState snake, String newDirection) {
        if (snake.getDirectionQueue() == null) {
            snake.setDirectionQueue(new java.util.ArrayDeque<>());
        }
        java.util.Deque<String> q = snake.getDirectionQueue();
        String currentDir = snake.getDirection();
        String effectiveDir = q.peekFirst() != null ? q.peekFirst() : currentDir;
            // currentDir, q, newDirection, effectiveDir);
        // 如果队列已有 3 个，新方向被丢弃
        if (q.size() >= 3) {
            return;
        }
        // 获取队列当前有效方向：队首方向（即将使用的），如果没有则取当前 direction
        String effectiveDirection = q.peekFirst();
        if (effectiveDirection == null) {
            effectiveDirection = snake.getDirection();
        }
        // 检查是否与有效方向相反
        if (isOpposite(effectiveDirection, newDirection)) {
            return;
        }
        // 检查队列中最后一个方向是否与 newDirection 相同
        String lastInQueue = q.peekLast();
        if (lastInQueue != null && newDirection.equals(lastInQueue)) {
            return;
        }
        q.addLast(newDirection);
        // 队列最前端的方向立刻生效到 directionNext
        snake.setDirectionNext(q.peekFirst());
    }

    private boolean isOpposite(String a, String b) {
        if (a == null || b == null) return false;
        return switch (a) {
            case "up" -> "down".equals(b);
            case "down" -> "up".equals(b);
            case "left" -> "right".equals(b);
            case "right" -> "left".equals(b);
            default -> false;
        };
    }
    @Override
    public void sendEmoji(String userCode,int emojiId){
        Integer roomCode = roomStateManager.getRoomCodeByUserCode(userCode);
        if (roomCode == null) {
            return;
        }
        RoomState roomState = roomStateManager.getOrInitRoom(roomCode);
        SnakeState snakeNow = new SnakeState();
        for(SnakeState snake:roomState.getSnakes()){
            if(snake.getType().equals("human")&&snake.getOwnerUserCode().equals(userCode)){
                snakeNow =  snake;
                break;
            }
        }
        if(snakeNow.getEmojiTimer()>0)return;
        EmojiMessageVO emojis = new EmojiMessageVO();
        emojis.setEmojiId(emojiId);
        emojis.setUserCode(userCode);
        String displayName = roomStateManager.getCodeName().get(userCode);
        emojis.setNickname(displayName);
        emojis.setTimestamp(System.currentTimeMillis());
        roomState.getRoomEmojis().add(emojis); // add 加到队尾，push 会加到队头破坏 FIFO
        snakeNow.setEmojiTimer(20);
    }

    public void finishGame(RoomState roomState) {
        for(SnakeState snake:roomState.getSnakes()){
            String ownerUserCode = snake.getOwnerUserCode();
            if (ownerUserCode == null || ownerUserCode.isBlank()) {
                continue;
            }
            try {
                int maxLength = RoomUtil.updateAndGetMaxLength(snake.getBody().size(), ownerUserCode, userMapper);
                snake.setMaxLength(maxLength);
            } catch (Exception e) {
            }
        }
        // 游戏结束，画面定格但房间保持开放，玩家不被踢出
        roomState.setStatus("finished");
        int roomCode = roomState.getRoomCode();
        roomSummaryManager.setStatus(roomCode, "finished");
        // 不重置 startTime，保留游戏结束时间点
        RoomSummaryVO summary = roomSummaryManager.getRoom(roomCode);
        broadcastService.broadcastRoomSummary(summary);
    }
    /**
     * gameTick: 150ms 固定频率处理所有游戏房间。
     * 每个房间独立 synchronized(roomState)，通过 gameRoomExecutor 并行处理。
     * tick 线程等待所有房间完成（最长 140ms），保持同步语义的同时并行化房间处理。
     */
    private long tickCount = 0;
    private volatile long lastCancelled = 0;
    private volatile long lastTickElapsedMs = 0;
    private volatile int lastPlayingRooms = 0;
    private final java.util.concurrent.atomic.AtomicLong inputCounter = new java.util.concurrent.atomic.AtomicLong(0);
    private long lastInputCount = 0;
    private long lastHealthTime = 0;

    @Scheduled(fixedRate = 150)
    public void gameTick() {
        tickCount++;
        long tickStart = System.currentTimeMillis();
        List<Callable<Void>> tasks = new ArrayList<>();
        int playingRooms = 0;
        for (RoomState roomState : roomStateManager.getRoomStates().values()) {
            if (!"playing".equals(roomState.getStatus())) {
                continue;
            }
            playingRooms++;
            int roomCode = roomState.getRoomCode();
            Set<String> members = roomStateManager.getRoomMembers().get(roomCode);
            int playerCount = members == null ? 0 : members.size();
            if (playerCount == 0) {
                roomState.setStatus("waiting");
                playingRooms--;
                continue;
            }

            tasks.add(() -> {
                synchronized (roomState) {
                    updateProps(roomState);
                    updateSnakes(roomState);
                    updateFruits(roomState);
                    handleRespawn(roomState);
                    delEmojis(roomState);
                    long now = System.currentTimeMillis();
                    long remain = 600 - (now - roomState.getGameStartTime()) / 1000;
                    if (remain < 0) {
                        remain = 0;
                    }
                    roomState.setCountdownMin(remain / 60);
                    roomState.setCountdownSecond(remain % 60);

                    if (remain == 0) {
                        finishGame(roomState);
                    }

                    broadcastService.broadcastRoomDelta(roomState);
                }
                return null;
            });
        }

        if (tasks.isEmpty()) {
            return;
        }

        try {
            List<Future<Void>> futures = gameRoomExecutor.invokeAll(tasks, 140, TimeUnit.MILLISECONDS);
            long tickElapsed = System.currentTimeMillis() - tickStart;
            long cancelled = futures.stream().filter(Future::isCancelled).count();
            int broadcastQueueSize = broadcastService.getBroadcastQueueSize();

            if (cancelled > 0) {
                System.out.println("gameTick 过载: " + cancelled + "/" + tasks.size() + " 房间未处理, tick耗时" + tickElapsed + "ms, 广播队列" + broadcastQueueSize);
            } else if (tickElapsed > 100 || broadcastQueueSize > 1500) {
                System.out.println("gameTick 预警: " + tasks.size() + " 房间, tick耗时" + tickElapsed + "ms, 广播队列" + broadcastQueueSize);
            } else if (tickCount % 20 == 0) {
                System.out.println("gameTick 心跳: #" + tickCount + " " + playingRooms + "房, tick" + tickElapsed + "ms, 队列" + broadcastQueueSize);
            }
            lastCancelled = cancelled;
            lastTickElapsedMs = tickElapsed;
            lastPlayingRooms = playingRooms;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // 独立于 gameTick 的健康监控，每 3 秒一次
    @Scheduled(fixedRate = 3000)
    public void healthMonitor() {
        int playingRooms = 0;
        int totalPlayers = 0;
        for (RoomState rs : roomStateManager.getRoomStates().values()) {
            if ("playing".equals(rs.getStatus())) {
                playingRooms++;
                Set<String> m = roomStateManager.getRoomMembers().get(rs.getRoomCode());
                if (m != null) totalPlayers += m.size();
            }
        }
        if (playingRooms == 0) return;

        long now = System.currentTimeMillis();
        long totalInputs = inputCounter.get();
        long intervalMs = lastHealthTime > 0 ? (now - lastHealthTime) : 3000;
        long inputRate = lastHealthTime > 0 ? (totalInputs - lastInputCount) * 1000 / intervalMs : 0;
        lastInputCount = totalInputs;
        lastHealthTime = now;

        int totalSessions = sessionContextManager.getSessionContextMap().size();
        int broadcastQueue = broadcastService.getBroadcastQueueSize();
        String verdict;
        if (lastCancelled > 0) {
            verdict = "!!! 过载:" + lastCancelled + "/" + lastPlayingRooms + "房跳过 !!!";
        } else if (inputRate < 100 && totalPlayers > 500) {
            verdict = "WARN input流速异常:" + inputRate + "/s";
        } else if (broadcastQueue > 1800) {
            verdict = "WARN 广播队列:" + broadcastQueue;
        } else if (broadcastQueue > 1000) {
            verdict = "广播队列偏高:" + broadcastQueue;
        } else {
            verdict = "正常";
        }
        System.out.println("[健康] " + verdict + " | WS:" + totalSessions + " 房:" + playingRooms + " 玩家:" + totalPlayers + " 输入:" + inputRate + "/s 队列:" + broadcastQueue);
    }

    public void assignHumanSnakeToNewPlayer(RoomState roomState, String userCode) {
        // 先检查用户是否已经拥有一条蛇（例如：关闭浏览器后短时间内重连）
        // 如果是，只需要更新 userCode→snakeIndex 映射，避免创建重复蛇
        for (int i = 0; i < roomState.getSnakes().size(); i++) {
            SnakeState snake = roomState.getSnakes().get(i);
            if (userCode.equals(snake.getOwnerUserCode())) {
                snake.setType("human");
                snake.setDirection(null);
                snake.setDirectionNext(null);
                if (snake.getDirectionQueue() != null) {
                    snake.getDirectionQueue().clear();
                }
                roomState.getUserCodeToSnakeIndex().put(userCode, i);
                return;
            }
        }

        // 没有已有蛇：找一条 AI 蛇替换
        for (int i = 0; i < roomState.getSnakes().size(); i++) {
            SnakeState snake = roomState.getSnakes().get(i);
            if ("ai".equals(snake.getType())) {

                // 1. AI蛇死亡并转水果
                snakeDieAndConvertToFruit(roomState, i, "SYSTEM_REASSIGN");
                // 2. 重新刷新这条蛇的位置
                roomUtil.refreshSnake(roomState, snake);

                // 3. 改成真人蛇
                snake.setType("human");
                snake.setOwnerUserCode(userCode);
//                snake.setSessionId(sessionId);

                // 4. 记录映射
                roomState.getUserCodeToSnakeIndex().put(userCode, i);

                // 5. 真人蛇方向先置空
                snake.setDirection(null);
                snake.setDirectionNext(null);

                return;
            }
        }

        // 没有可替换的 AI 蛇：动态新增一条蛇给新玩家
        SnakeState newSnake = new SnakeState();
        newSnake.setBody(new ArrayList<>());
        newSnake.setDirection(null);
        newSnake.setDirectionNext(null);
        newSnake.setAlive(true);
        newSnake.setRespawnTimer(0);
        newSnake.setChangeDirTimer(0);
        newSnake.setDirX(0);
        newSnake.setDirY(0);
        newSnake.setMoveInterval(2);
        newSnake.setMoveCounter(0);
        newSnake.setEmojiTimer(0);
        SnakeState.PropsTimer propsTimer = new SnakeState.PropsTimer();
        propsTimer.setSpeedUp(0);
        propsTimer.setSpeedDown(0);
        propsTimer.setRevealAll(0);
        propsTimer.setFog(0);
        newSnake.setPropsTimer(propsTimer);
        newSnake.setType("human");
        newSnake.setOwnerUserCode(userCode);
        roomUtil.refreshSnake(roomState, newSnake);
        int newIndex = roomState.getSnakes().size();
        roomState.getSnakes().add(newSnake);
        roomState.getUserCodeToSnakeIndex().put(userCode, newIndex);
    }

    private void snakeDieAndConvertToFruit(RoomState roomState, int snakeIndex, String deathReason) {
        SnakeState snake = roomState.getSnakes().get(snakeIndex);
        int[][] map = roomState.getMap();
        snake.setAlive(false);
        snake.setDeathReason(deathReason);
        snake.setRespawnTimer(20); // 统一 20 回合复活，后面你再细分真人/人机
        String ownerUserCode = snake.getOwnerUserCode();
        if (ownerUserCode != null && !ownerUserCode.isBlank()) {
            int maxLength = RoomUtil.updateAndGetMaxLength(snake.getBody().size(), ownerUserCode, userMapper);
            snake.setMaxLength(maxLength);
        }
        int k = 0;
        for (Node node : snake.getBody()) {
            int x = node.getX();
            int y = node.getY();

            if (k < 3) {
                map[x][y] = 0;
                ++k;
            } else {
                map[x][y] = 2;
                roomState.getFruits().add(x + "," + y);
                k=0;
            }
        }

        snake.getBody().clear();
    }

    private void handlePlayerLeaveSnake(RoomState roomState, int snakeIndex) {
        SnakeState snake = roomState.getSnakes().get(snakeIndex);

        // 1. 立即死亡并转水果
        snakeDieAndConvertToFruit(roomState, snakeIndex, "ROOM_LEAVE");
        // 2. 标记为 5 秒后复活
        snake.setRespawnTimer(5);

        // 3. 把蛇的状态改为人机
        snake.setType("ai");
        snake.setOwnerUserCode(null);
    }

    public void updateSnakes(RoomState roomState) {
        if (!"playing".equals(roomState.getStatus())) {
            return;
        }

        int[][] map = roomState.getMap();
        List<SnakeState> snakes = roomState.getSnakes();
        Set<Integer> pendingHeadCollisionKills = new HashSet<>();

        for (int i = 0; i < snakes.size(); i++) {
            SnakeState snake = snakes.get(i);

            if (!snake.isAlive()) {
                continue;
            }

            if (snake.getBody() == null || snake.getBody().isEmpty()) {
                continue;
            }

            Node head = snake.getBody().get(0);

            // 玩家蛇：每 tick 从队列取一个方向
            if (snake.getType().equals("human")) {
                if(snake.getEmojiTimer()>0){
                    snake.setEmojiTimer(snake.getEmojiTimer()-1);
                }
                // 优先从队列取方向
                String nextDir = null;
                if (snake.getDirectionQueue() != null && !snake.getDirectionQueue().isEmpty()) {
                    nextDir = snake.getDirectionQueue().pollFirst();
                }
                // 如果取出的方向与当前方向相反，丢弃它（防止掉头秒死）
                if (nextDir != null && !isOpposite(nextDir, snake.getDirection())) {
                    snake.setDirection(nextDir);
                    applyDirection(snake);
                }
                // 如果队列空了但之前设了 directionNext，也消费（兼容旧代码）
                if (nextDir == null && snake.getDirectionNext() != null
                        && !isOpposite(snake.getDirectionNext(), snake.getDirection())) {
                    snake.setDirection(snake.getDirectionNext());
                    applyDirection(snake);
                    snake.setDirectionNext(null);
                }
                if(snake.getDirection() == null) {
                    continue;
                }
            } else {
                // AI 蛇：这里先简单随机改变方向
                if (snake.getChangeDirTimer() == 0) {
                    String next = aiChangeDirection(head.getX(), head.getY(), snake.getDirection(), map);
                    if (next == null) {
                        snake.setDirection(null);
                        snake.setDirX(0);
                        snake.setDirY(0);
                        snake.setChangeDirTimer(1);
                        continue;
                    }
                    snake.setDirection(next);
                    applyDirection(snake);
                    snake.setChangeDirTimer((int) (Math.random() * 10) + 1);
                } else {
                    snake.setChangeDirTimer(snake.getChangeDirTimer() - 1);
                }
            }

            // 移动计数
            snake.setMoveCounter(snake.getMoveCounter() + 1);
            if (snake.getMoveCounter() < snake.getMoveInterval()) {
                continue;
            }
            snake.setMoveCounter(0);

            int newX = head.getX() + snake.getDirX();
            int newY = head.getY() + snake.getDirY();
            if (newX < 0 || newX >= MAP_SIZE || newY < 0 || newY >= MAP_SIZE) {
                snakeDieAndConvertToFruit(roomState, i, SnakeState.DEATH_REASON_WALL);
                continue;
            }

            Integer headCollisionIndex = findAliveSnakeHeadAt(snakes, newX, newY, i);
            if (headCollisionIndex != null) {
                pendingHeadCollisionKills.add(i);
                pendingHeadCollisionKills.add(headCollisionIndex);
                continue;
            }

            int cell = map[newX][newY];

            // 撞蛇身
            if (cell == 1) {
                snakeDieAndConvertToFruit(roomState, i, SnakeState.DEATH_REASON_BODY);
                continue;
            }

            // 吃水果
            if (cell == 2) {
                Node newHead = new Node();
                newHead.setX(newX);
                newHead.setY(newY);

                snake.getBody().add(0, newHead);
                map[newX][newY] = 1;
                roomState.getFruits().remove(newX + "," + newY);
                continue;
            }

            // 吃道具
            if (cell >= 3 && cell <= 6) {
                Node newHead = new Node();
                newHead.setX(newX);
                newHead.setY(newY);

                snake.getBody().add(0, newHead);
                map[newX][newY] = 1;

                applyPropToSnake(snake, cell);

                removePropFromRoom(roomState, cell, newX, newY);
                continue;
            }

            // 普通移动
            Node newHead = new Node();
            newHead.setX(newX);
            newHead.setY(newY);

            Node tail = snake.getBody().remove(snake.getBody().size() - 1);
            map[tail.getX()][tail.getY()] = 0;

            snake.getBody().add(0, newHead);
            map[newX][newY] = 1;
        }

        for (Integer snakeIndex : pendingHeadCollisionKills) {
            if (snakeIndex == null || snakeIndex < 0 || snakeIndex >= snakes.size()) {
                continue;
            }
            SnakeState snake = snakes.get(snakeIndex);
            if (snake != null && snake.isAlive()) {
                snakeDieAndConvertToFruit(roomState, snakeIndex, SnakeState.DEATH_REASON_HEAD);
            }
        }
    }

    private Integer findAliveSnakeHeadAt(List<SnakeState> snakes, int x, int y, int excludeIndex) {
        for (int i = 0; i < snakes.size(); i++) {
            if (i == excludeIndex) {
                continue;
            }
            SnakeState candidate = snakes.get(i);
            if (candidate == null || !candidate.isAlive() || candidate.getBody() == null || candidate.getBody().isEmpty()) {
                continue;
            }

            Node head = candidate.getBody().get(0);
            if (head.getX() == x && head.getY() == y) {
                return i;
            }
        }
        return null;
    }
    public void updateProps(RoomState roomState) {
        if (!"playing".equals(roomState.getStatus())) {
            return;
        }

        for (SnakeState snake : roomState.getSnakes()) {
            if (snake.getPropsTimer() == null) {
                continue;
            }

            SnakeState.PropsTimer timer = snake.getPropsTimer();
            if (snake.getPropsTimer().getSpeedUp() == snake.getPropsTimer().getSpeedDown())
                snake.setMoveInterval(2);
            if (timer.getSpeedUp() > 0) timer.setSpeedUp(timer.getSpeedUp() - 1);
            if (timer.getSpeedDown() > 0) timer.setSpeedDown(timer.getSpeedDown() - 1);
            if (timer.getRevealAll() > 0) timer.setRevealAll(timer.getRevealAll() - 1);
            if (timer.getFog() > 0) timer.setFog(timer.getFog() - 1);
        }
    }
    public void updateFruits(RoomState roomState) {
        if (!"playing".equals(roomState.getStatus())) {
            return;
        }

        int[][] map = roomState.getMap();

        while (roomState.getFruits().size() < 500) {
            int[] pos = roomUtil.randomPlace(map);
            int x = pos[0];
            int y = pos[1];

            map[x][y] = 2;
            roomState.getFruits().add(x + "," + y);
        }
    }
    public void handleRespawn(RoomState roomState) {
        if (!"playing".equals(roomState.getStatus())) {
            return;
        }

        for (SnakeState snake : roomState.getSnakes()) {
            if (snake.isAlive()) {
                continue;
            }

            if (snake.getRespawnTimer() > 0) {
                snake.setRespawnTimer(snake.getRespawnTimer() - 1);
                continue;
            }

            // 倒计时结束，重新刷新
            roomUtil.refreshSnake(roomState, snake);
            snake.setDeathReason(null);

            // 如果是离开的玩家，复活后变 AI
            if (snake.getOwnerUserCode() == null) {
                snake.setType("ai");
            }
        }
    }
    public void delEmojis(RoomState roomState) {
        long now = System.currentTimeMillis();
        // 删除过期表情（每个表情独立 5s 生存时间）
        while (!roomState.getRoomEmojis().isEmpty()) {
            EmojiMessageVO emoji = roomState.getRoomEmojis().peek();
            if (emoji == null) {
                break;
            }
            if (now - emoji.getTimestamp() > 5000) {
                roomState.getRoomEmojis().poll();
                continue;
            }
            break;
        }
        // FIFO：最多保留 5 条，超出则删除最早的
        while (roomState.getRoomEmojis().size() > 5) {
            roomState.getRoomEmojis().poll();
        }
    }
    private void applyDirection(SnakeState snake) {
        if ("up".equals(snake.getDirection())) {
            snake.setDirX(0);
            snake.setDirY(-1);
        } else if ("down".equals(snake.getDirection())) {
            snake.setDirX(0);
            snake.setDirY(1);
        } else if ("left".equals(snake.getDirection())) {
            snake.setDirX(-1);
            snake.setDirY(0);
        } else if ("right".equals(snake.getDirection())) {
            snake.setDirX(1);
            snake.setDirY(0);
        }
    }
    private String aiChangeDirection(int x, int y, String direction, int[][] map) {
        String[] choices = new String[]{"up", "down", "left", "right"};
        int start = (int) (Math.random() * choices.length);

        for (int i = 0; i < choices.length; i++) {
            String next = choices[(start + i) % choices.length];
            if (!isDirectionAvailable(x, y, next, map)) {
                continue;
            }
            if (isOppositeDirection(direction, next)) {
                continue;
            }
            return next;
        }

        return null;
    }

    private boolean isDirectionAvailable(int x, int y, String direction, int[][] map) {
        if ("up".equals(direction)) {
            return y - 1 >= 0 && map[x][y - 1] != 1;
        }
        if ("down".equals(direction)) {
            return y + 1 < MAP_SIZE && map[x][y + 1] != 1;
        }
        if ("left".equals(direction)) {
            return x - 1 >= 0 && map[x - 1][y] != 1;
        }
        if ("right".equals(direction)) {
            return x + 1 < MAP_SIZE && map[x + 1][y] != 1;
        }
        return false;
    }

    private boolean isOppositeDirection(String currentDirection, String nextDirection) {
        if (currentDirection == null || nextDirection == null) {
            return false;
        }
        return ("up".equals(currentDirection) && "down".equals(nextDirection))
                || ("down".equals(currentDirection) && "up".equals(nextDirection))
                || ("left".equals(currentDirection) && "right".equals(nextDirection))
                || ("right".equals(currentDirection) && "left".equals(nextDirection));
    }
    private void applyPropToSnake(SnakeState snake, int cell) {
        if (cell == 3) {
            snake.getPropsTimer().setSpeedUp(snake.getPropsTimer().getSpeedUp() + 8);
        } else if (cell == 4) {
            snake.getPropsTimer().setSpeedDown(snake.getPropsTimer().getSpeedDown() + 8);
        } else if (cell == 5) {
            snake.getPropsTimer().setRevealAll(snake.getPropsTimer().getRevealAll() + 8);
        } else if (cell == 6) {
            snake.getPropsTimer().setFog(snake.getPropsTimer().getFog() + 8);
        }

        if (snake.getPropsTimer().getSpeedUp() > snake.getPropsTimer().getSpeedDown()) {
            snake.setMoveInterval(1);
            snake.getPropsTimer().setSpeedUp(snake.getPropsTimer().getSpeedUp() - snake.getPropsTimer().getSpeedDown());
            snake.getPropsTimer().setSpeedDown(0);
        } else if (snake.getPropsTimer().getSpeedUp() < snake.getPropsTimer().getSpeedDown()) {
            snake.setMoveInterval(4);
            snake.getPropsTimer().setSpeedDown(snake.getPropsTimer().getSpeedDown() - snake.getPropsTimer().getSpeedUp());
            snake.getPropsTimer().setSpeedUp(0);
        } else {
            snake.setMoveInterval(2);
            snake.getPropsTimer().setSpeedUp(0);
            snake.getPropsTimer().setSpeedDown(0);
        }
    }
    private void removePropFromRoom(RoomState roomState, int cell, int x, int y) {
        String key = x + "," + y;
        int x2,y2;
        int[] a = roomUtil.randomPlace(roomState.getMap());
        x2 = a[0];
        y2 = a[1];
        String key2 = x2 + "," + y2;
        if (cell == 3) {
            roomState.getSpeedUp().remove(key);
            roomState.getSpeedUp().add(key2);
            roomState.getMap()[x2][y2] = 3;
        } else if (cell == 4) {
            roomState.getSpeedDown().remove(key);
            roomState.getSpeedDown().add(key2);
            roomState.getMap()[x2][y2] = 4;
        } else if (cell == 5) {
            roomState.getRevealAll().remove(key);
            roomState.getRevealAll().add(key2);
            roomState.getMap()[x2][y2] = 5;
        } else if (cell == 6) {
            roomState.getFog().remove(key);
            roomState.getFog().add(key2);
            roomState.getMap()[x2][y2] = 6;
        }

        roomState.getMap()[x][y] = 1;
    }
}
