package com.example.snake_back.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@TableName("friendships")
@Data
public class Friendship {
    private String id;

    @TableField("user_code_1")
    private Integer userCode1;

    @TableField("user_code_2")
    private Integer userCode2;

    @TableField("created_at")
    private String createdAt;
}