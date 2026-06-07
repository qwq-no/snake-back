package com.example.snake_back.pojo.dto;

import lombok.Data;
import org.springframework.web.socket.WebSocketSession;

@Data
public class SessionContextDTO {
    private WebSocketSession session;
    private String userCode;
    private String nickname;
    private String pageType;
    private String roomCode;
    private String status;          //HOME：在首页，ROOM_SELECT：在选房间页。ROOM_PREPARE：在房间准备页，
                                    // ONLINE：在联机中，IN_SINGLE_GAME：单人模式中,TALK：在聊天中
    private long lastHeartbeat;
    private boolean effective;      // 判断是否在线
    private long heartbeatTimeout;
    /** 用户本次"上线"的时间戳（刷新页面时保留，心跳过期后重置），用于群聊消息可见范围过滤 */
    private long groupChatJoinTime;
}