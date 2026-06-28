package com.example.snake_back.manager;

import com.example.snake_back.pojo.dto.RoomState;
import com.example.snake_back.pojo.vo.EmojiMessageVO;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Data
@Component
public class RoomStateManager {
    private final Map<Integer, RoomState> roomStates = new ConcurrentHashMap<>();       //每个房间当前的状态
    private final Map<Integer, Set<String>> roomReadyUsers = new ConcurrentHashMap<>(); //每个房间准备情况
    private final Map<Integer, Set<String>> roomMembers = new ConcurrentHashMap<>();    //每个房间都有谁
    private final Map<String, Integer> userRoom = new ConcurrentHashMap<>();            //每个人都在哪个房间
    private final Map<String,String> codeName = new ConcurrentHashMap<>();              //每个人的id对应的昵称

    public RoomState getOrInitRoom(int roomCode) {
        RoomState roomState = roomStates.get(roomCode);
        if (roomState == null) {
            roomState = initRoom(roomCode);
            roomStates.put(roomCode, roomState);
        }
        return roomState;
    }
    //这是准备阶段对房间的初始化
    private RoomState initRoom(int roomCode) {
        RoomState roomState = new RoomState();
        roomState.setRoomCode(roomCode);
        roomState.setStatus("waiting");
        roomState.setCountdownMin(10);
        roomState.setCountdownSecond(0);
        roomState.setSnakes(new ArrayList<>());
        roomState.setFruits(new HashSet<>());
        roomState.setSpeedUp(new HashSet<>());
        roomState.setSpeedDown(new HashSet<>());
        roomState.setRevealAll(new HashSet<>());
        roomState.setFog(new HashSet<>());
        roomState.setMap(new int[102][102]);
        roomState.setRoomEmojis(new ArrayDeque<>());
        roomReadyUsers.put(roomCode, ConcurrentHashMap.newKeySet());
        roomMembers.put(roomCode, ConcurrentHashMap.newKeySet());
        return roomState;
    }
    public void addUserToRoom(int roomCode, String userCode) {
        roomMembers.computeIfAbsent(roomCode, k -> ConcurrentHashMap.newKeySet()).add(userCode);
        userRoom.put(userCode, roomCode);
    }

    public void removeUserFromRoom(String userCode) {
        Integer roomCode = userRoom.remove(userCode);
        if (roomCode != null) {
            roomMembers.getOrDefault(roomCode, Set.of()).remove(userCode);
            roomReadyUsers.getOrDefault(roomCode, Set.of()).remove(userCode);
        }
    }

    public void ready(String userCode) {
        Integer roomCode = userRoom.get(userCode);
        if (roomCode == null) {
            return;
        }
        roomReadyUsers.computeIfAbsent(roomCode, k -> ConcurrentHashMap.newKeySet()).add(userCode);
    }

    public Integer getRoomCodeByUserCode(String userCode) {
        return userRoom.get(userCode);
    }

    public void unready(String userCode) {
        Integer roomCode = userRoom.get(userCode);
        if (roomCode == null) return;

        Set<String> readySet = roomReadyUsers.get(roomCode);
        if (readySet != null) {
            readySet.remove(userCode);
        }
    }

    public Deque<EmojiMessageVO> getEmojiQueue(Integer roomCode) {
        RoomState roomState = roomStates.get(roomCode);
        return roomState.getRoomEmojis();
    }
}