package com.example.snake_back.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@TableName("private_messages")
@Data
public class PrivateMessage {
    private String id;
    private Integer fromUserCode;
    private Integer toUserCode;
    private String content;
    private String createdAt;
}
