package com.example.snake_back.service.Impl;

import com.example.snake_back.common.utils.RoomUtil;
import com.example.snake_back.manager.RoomStateManager;
import com.example.snake_back.manager.RoomSummaryManager;
import com.example.snake_back.manager.SessionContextManager;
import com.example.snake_back.pojo.dto.RoomState;
import com.example.snake_back.pojo.dto.SnakeState;
import com.example.snake_back.pojo.vo.RoomSummaryVO;
import com.example.snake_back.service.BroadcastService;
import com.example.snake_back.service.PrepareService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Set;

@Service
public class PrepareServiceImpl implements PrepareService {
    private final RoomStateManager  roomStateManager;
    private final RoomSummaryManager roomSummaryManager;
    private final RoomUtil roomUtil;
    private final SessionContextManager sessionContextManager;
    private final BroadcastService broadcastService;

    @Value("${app.game.max-snakes:10}")
    private int maxSnakes;

    Integer MAP_SIZE = 102;
    Integer FRUIT_COUNT = 500;
    Integer PROP_COUNT = 8;

    public PrepareServiceImpl(RoomStateManager roomStateManager, RoomSummaryManager roomSummaryManager, RoomUtil roomUtil, SessionContextManager sessionContextManager,
                              BroadcastService broadcastService) {
        this.roomStateManager = roomStateManager;
        this.roomSummaryManager = roomSummaryManager;
        this.roomUtil = roomUtil;
        this.sessionContextManager = sessionContextManager;
        this.broadcastService = broadcastService;
    }

    @Override
    public void leaveRoom(String userCode) {
        Integer roomCode = roomStateManager.getRoomCodeByUserCode(userCode);
        if (roomCode == null) return;
        RoomState roomState = roomStateManager.getRoomStates().get(roomCode);

        roomStateManager.removeUserFromRoom(userCode);
        RoomSummaryVO updatedSummary = roomSummaryManager.applyMemberChange(roomCode, userCode, false);
        broadcastService.broadcastRoomSummary(updatedSummary);
        Set<String> members = roomStateManager.getRoomMembers().get(roomCode);
        if (members == null || members.isEmpty()) {
            roomUtil.resetRoom(roomState);
        }
    }

    @Override
    public void ready(String userCode) {
        try {
            Integer roomCode = roomStateManager.getRoomCodeByUserCode(userCode);
            roomStateManager.ready(userCode);
            if (roomCode == null) return;
            Set<String> readyUsers = roomStateManager.getRoomReadyUsers().get(roomCode);
            Set<String> members = roomStateManager.getRoomMembers().get(roomCode);
            if (readyUsers != null && members != null && readyUsers.size() == members.size()) {
                RoomState roomState = roomStateManager.getOrInitRoom(roomCode);
                roomState.setStatus("playing");
                for (String user : members) {
                    String sessionId = sessionContextManager.getUserCodeToSessionIdMap().get(user);
                    if (sessionId == null) continue;
                    var ctx = sessionContextManager.getSessionContextMap().get(sessionId);
                    if (ctx != null) {
                        ctx.setHeartbeatTimeout(15000L);
                        ctx.setLastHeartbeat(System.currentTimeMillis());
                    }
                }
                initGame(roomCode);
                RoomState startedRoom = roomStateManager.getOrInitRoom(roomCode);
                roomSummaryManager.startGame(roomCode, startedRoom.getGameStartTime());
                RoomSummaryVO startedSummary = roomSummaryManager.getRoom(roomCode);
                broadcastService.broadcastRoomSummary(startedSummary);
            }
            broadcastService.broadcastRoomLobbyState(roomCode);
        }catch (Exception e){e.printStackTrace();}
    }
    @Override
    public void unready(String userCode) {
        Integer roomCode = roomStateManager.getRoomCodeByUserCode(userCode);
        if (roomCode == null) return;
        roomStateManager.unready(userCode);
        broadcastService.broadcastRoomLobbyState(roomCode);
    }

    public void initGame(int roomCode) {
        RoomState roomState = roomStateManager.getRoomStates().get(roomCode);
        if (roomState == null) {
            roomState = roomStateManager.getOrInitRoom(roomCode);
        }

        synchronized (roomState) {
            roomState.setGameStartTime(System.currentTimeMillis());

            // 1. 清空地图
            int[][] map = roomState.getMap();
            for (int row = 0; row < MAP_SIZE; row++) {
                for (int col = 0; col < MAP_SIZE; col++) {
                    map[row][col] = 0;
                }
            }

            // 2. 画边界
            for (int i = 0; i < MAP_SIZE; i++) {
                map[0][i] = 1;
                map[MAP_SIZE - 1][i] = 1;
                map[i][0] = 1;
                map[i][MAP_SIZE - 1] = 1;
            }

            // 3. 清空水果和道具
            roomState.getFruits().clear();
            roomState.getSpeedUp().clear();
            roomState.getSpeedDown().clear();
            roomState.getRevealAll().clear();
            roomState.getFog().clear();

            // 4. 清空蛇
            roomState.getSnakes().clear();

            // 5. 重置房间状态
            roomState.setStatus("playing");
            roomState.setCountdownMin(10);
            roomState.setCountdownSecond(0);

            // 6. 初始化蛇
            initSnakes(roomState);

            // 7. 初始化道具
            initProps(roomState);

            // 8. 初始化水果
            initFruit(roomState);

            // 9. 立即发送全量快照 + 缓存，后续 gameTick 直接走 delta
            broadcastService.broadcastRoomState(roomState);
        }
    }

    private void initFruit(RoomState roomState) {
        int[][] map = roomState.getMap();

        while (roomState.getFruits().size() < FRUIT_COUNT) {
            int[] location = roomUtil.randomPlace(map);
            int x = location[0];
            int y = location[1];

            map[x][y] = 2;
            roomState.getFruits().add(x + "," + y);
        }
    }

    private void initProps(RoomState roomState) {
        int[][] map = roomState.getMap();

        initSingleProp( map, 3, roomState.getSpeedUp());
        initSingleProp( map, 4, roomState.getSpeedDown());
        initSingleProp( map, 5, roomState.getRevealAll());
        initSingleProp( map, 6, roomState.getFog());
    }

    private void initSnakes(RoomState roomState) {
        roomState.getSnakes().clear();
        roomState.getUserCodeToSnakeIndex().clear();

        int roomCode = roomState.getRoomCode();
        int playerCount = roomStateManager.getRoomMembers().get(roomCode).size();

        for (int i = 0; i < maxSnakes; i++) {
            SnakeState snake = new SnakeState();

            snake.setBody(new ArrayList<>());
            snake.setDirection(null);
            snake.setDirectionNext(null);
            snake.setAlive(true);
            snake.setRespawnTimer(0);
            snake.setChangeDirTimer(0);
            snake.setDirX(0);
            snake.setDirY(0);
            snake.setMoveInterval(2);
            snake.setMoveCounter(0);
            snake.setEmojiTimer(0);

            SnakeState.PropsTimer propsTimer = new SnakeState.PropsTimer();
            propsTimer.setSpeedUp(0);
            propsTimer.setSpeedDown(0);
            propsTimer.setRevealAll(0);
            propsTimer.setFog(0);
            snake.setPropsTimer(propsTimer);

            if (i < playerCount) {
                snake.setType("human");
            } else {
                snake.setType("ai");
            }

            roomUtil.refreshSnake(roomState, snake);
            roomState.getSnakes().add(snake);

            if (i < playerCount) {
                snake.setDirection(null);
            }
        }

        // 根据当前 roomUsers 里的真人玩家，建立 userCode -> snakeIndex 映射
        int idx = 0;
        int snakeCount = roomState.getSnakes().size();
        for (String userCode : roomStateManager.getRoomMembers().get(roomState.getRoomCode())) {
            if (idx >= playerCount || idx >= snakeCount) {
                break;
            }
            roomState.getUserCodeToSnakeIndex().put(userCode, idx);
            SnakeState snake = roomState.getSnakes().get(idx);
            snake.setOwnerUserCode(userCode);
            idx++;
        }
    }

    private void initSingleProp(int[][] map, int propType, Set<String> propSet) {
        while (propSet.size() < PROP_COUNT) {
            int[] location = roomUtil.randomPlace(map);
            int x = location[0];
            int y = location[1];

            map[x][y] = propType;
            propSet.add(x + "," + y);
        }
    }

}
