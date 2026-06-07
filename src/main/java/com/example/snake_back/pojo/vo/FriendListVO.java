package com.example.snake_back.pojo.vo;

import lombok.Data;

@Data
public class FriendListVO {
    private Integer userCode;
    private String userName;
    private String status;
    private Integer roomCode; // 好友当前所在房间号，不在房间时为 null
}