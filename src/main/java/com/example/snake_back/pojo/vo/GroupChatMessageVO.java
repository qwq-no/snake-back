package com.example.snake_back.pojo.vo;

import lombok.Data;

@Data
public class GroupChatMessageVO {
    private String nickname;
    private String userCode;
    private String content;
    private long timestamp;
}
