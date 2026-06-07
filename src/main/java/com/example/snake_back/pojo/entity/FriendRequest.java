package com.example.snake_back.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@TableName("friend_requests")
@Data
public class FriendRequest {
    private String id;

    @TableField("from_user_code")
    private Integer fromUserCode;

    @TableField("to_user_code")
    private Integer toUserCode;

    private String status = "pending";

    @TableField("created_at")
    private String createdAt;

    @TableField("updated_at")
    private String updatedAt;
}