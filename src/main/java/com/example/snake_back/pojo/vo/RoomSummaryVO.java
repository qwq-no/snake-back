package com.example.snake_back.pojo.vo;

import lombok.Data;

@Data
public class RoomSummaryVO {
    private Integer roomCode;
    private int playerCount;
    private long startTime;
    private String[] userCodes;
    private String status;
}