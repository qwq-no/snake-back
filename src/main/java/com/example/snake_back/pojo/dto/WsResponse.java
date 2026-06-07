package com.example.snake_back.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WsResponse<T> {
    private String pageType; // 页面/场景
    private String type;     // 业务动作
    private T data;          // 页面专属数据
}