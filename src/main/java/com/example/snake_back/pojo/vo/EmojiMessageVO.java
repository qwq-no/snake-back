package com.example.snake_back.pojo.vo;

import lombok.Data;

@Data
public class EmojiMessageVO {
    private String userCode;
    private String nickname;
    private int emojiId;
    private long timestamp;
}
