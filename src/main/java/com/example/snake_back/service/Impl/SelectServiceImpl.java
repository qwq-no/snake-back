package com.example.snake_back.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.snake_back.common.utils.PageUtil;
import com.example.snake_back.manager.RoomStateManager;
import com.example.snake_back.manager.RoomSummaryManager;
import com.example.snake_back.mapper.UserMapper;
import com.example.snake_back.pojo.dto.RoomState;
import com.example.snake_back.pojo.entity.User;
import com.example.snake_back.pojo.vo.RoomSummaryVO;
import com.example.snake_back.service.BroadcastService;
import com.example.snake_back.service.OnlineService;
import com.example.snake_back.service.SelectService;
import org.springframework.stereotype.Service;

@Service
public class SelectServiceImpl implements SelectService {

    private final RoomSummaryManager roomSummaryManager;
    private final RoomStateManager roomStateManager;
    private final UserMapper userMapper;
    private final BroadcastService broadcastService;
    private final OnlineService onlineService;
    private final FriendshipServiceImpl friendshipService;
    private final PageUtil pageUtil;

    public SelectServiceImpl(RoomSummaryManager roomSummaryManager,RoomStateManager roomStateManager, UserMapper userMapper,
                             BroadcastService broadcastService,OnlineService onlineService,FriendshipServiceImpl friendshipService,
                             PageUtil pageUtil) {
        this.roomSummaryManager = roomSummaryManager;
        this.roomStateManager = roomStateManager;
        this.userMapper = userMapper;
        this.broadcastService = broadcastService;
        this.onlineService = onlineService;
        this.friendshipService = friendshipService;
        this.pageUtil = pageUtil;
    }
    @Override
    public void joinRoom(int roomCode,String userCode) {
        RoomState roomState = roomStateManager.getOrInitRoom(roomCode);
        roomStateManager.addUserToRoom(roomCode, userCode);
        if(!roomStateManager.getCodeName().containsKey(userCode)){
            String displayName = userMapper.selectOne(new QueryWrapper<User>().eq("user_code", userCode)).getDisplayName();
            roomStateManager.getCodeName().put(userCode,displayName);
        }
        RoomSummaryVO updatedSummary = roomSummaryManager.applyMemberChange(roomCode, userCode, true);
        broadcastService.broadcastRoomSummary(updatedSummary);
        if(roomState.getStatus().equals("playing")){
            synchronized (roomState) {
                onlineService.assignHumanSnakeToNewPlayer(roomState, userCode);
                broadcastService.broadcastRoomState(roomState);
            }
        } else if ("finished".equals(roomState.getStatus())) {
            // 游戏已结束：发送房间 snapshot 让新进玩家看到结束画面，但不分配蛇
            broadcastService.broadcastRoomState(roomState);
        } else {
            broadcastService.broadcastRoomLobbyState(roomCode);
        }
    }
    /**
     * 获取大厅房间列表
     */
    @Override
    public RoomSummaryVO[] getRoomSummaries() {
        return roomSummaryManager.getAllRooms();
    }

    @Override
    public void roomSummary() {
        RoomSummaryVO[] roomSummaries = roomSummaryManager.getAllRooms();
        broadcastService.broadcastRoomSummaries(roomSummaries);
    }

    @Override
    public void sendRoomSummaries(String userCode) {
        broadcastService.sendRoomSummaries(userCode);
    }
}