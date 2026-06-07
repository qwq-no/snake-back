package com.example.snake_back.pojo.vo;

import lombok.Data;

import java.util.List;

@Data
public class RoomDeltaVO {
    private long serverTime;
    private String status;
    private Long remainingSeconds;

    private List<SnakeDeltaVO> snakeDeltas;

    private List<PointVO> fruitAdded;
    private List<PointVO> fruitRemoved;

    private List<PointVO> speedUpAdded;
    private List<PointVO> speedUpRemoved;
    private List<PointVO> speedDownAdded;
    private List<PointVO> speedDownRemoved;
    private List<PointVO> revealAllAdded;
    private List<PointVO> revealAllRemoved;
    private List<PointVO> fogAdded;
    private List<PointVO> fogRemoved;

    private List<EmojiMessageVO> roomEmojis;
}