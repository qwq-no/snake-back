package com.example.snake_back.pojo.vo;

import lombok.Data;

@Data
public class FriendRequestListVO {
    private String requestId;
    private Integer fromUserCode;
    private String fromUserName;
}