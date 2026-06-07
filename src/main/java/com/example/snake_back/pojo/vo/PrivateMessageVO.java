package com.example.snake_back.pojo.vo;

import lombok.Data;

@Data
public class PrivateMessageVO {
    private String id;
    private Integer fromUserCode;
    private Integer toUserCode;
    private String content;
    private String createdAt;
}
