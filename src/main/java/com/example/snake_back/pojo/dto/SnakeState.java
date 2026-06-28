package com.example.snake_back.pojo.dto;

import lombok.Data;

import java.util.Deque;
import java.util.List;

@Data
public class SnakeState {
    public static final String DEATH_REASON_WALL = "WALL_COLLISION";
    public static final String DEATH_REASON_BODY = "BODY_COLLISION";
    public static final String DEATH_REASON_HEAD = "HEAD_COLLISION";

    private List<Node> body;               // 蛇身
    private String direction;              // 当前方向
    private String directionNext;          // 下一步方向
    private boolean alive;                 // 是否存活
    private int respawnTimer;              // 重生倒计时
    private int changeDirTimer;            // 转向冷却
    private int dirX;                      // 当前移动方向 x
    private int dirY;                      // 当前移动方向 y
    private int moveInterval;              // 移动间隔
    private int moveCounter;               // 移动计数
    private PropsTimer propsTimer;         // 道具计时器
    private int emojiTimer;               // 表情包倒计时
    private String type;                   // human / ai
    private String ownerUserCode;          // 真人蛇对应的用户
    private String sessionId;              // 对应的 WebSocket sessionId
    private int maxLength;                 // 蛇的最大长度
    private String deathReason;            // 最近一次死亡原因
    private Deque<String> directionQueue;  // 方向输入队列，最多 3 个，防快速反向

    @Data
    public static class PropsTimer {
        private int speedUp;
        private int speedDown;
        private int revealAll;
        private int fog;
    }
}