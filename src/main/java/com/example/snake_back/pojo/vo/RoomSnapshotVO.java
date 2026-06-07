package com.example.snake_back.pojo.vo;

import lombok.Data;

import java.util.Deque;
import java.util.List;
import java.util.Set;

@Data
public class RoomSnapshotVO {
    private Integer roomCode;
    private long countdownMin;
    private long countdownSecond;
    private long gameStartTime;
    private String status;

    private int[][] map;
    private List<SnakeSnapshotVO> snakes;

    private Set<String> fruits;
    private Set<String> speedUp;
    private Set<String> speedDown;
    private Set<String> revealAll;
    private Set<String> fog;

    private Deque<EmojiMessageVO> roomEmojis;
}