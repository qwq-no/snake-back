package com.example.snake_back.pojo.vo;

import lombok.Data;

import java.util.List;

@Data
public class SnakeSnapshotVO {
    private List<PointVO> body;
    private boolean alive;
    private int emojiTimer;
    private int revealAllTimer;
    private int fogTimer;
    private String ownerUserCode;
    private Integer maxLength;
    private String deathReason;
    private String type; // human / ai
}