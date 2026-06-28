package com.example.snake_back.pojo.vo;

import lombok.Data;

import java.util.List;

@Data
public class SnakeDeltaVO {
    public static final String TYPE_MOVE = "MOVE";
    public static final String TYPE_DIE = "DIE";
    public static final String TYPE_RESPAWN = "RESPAWN";
    public static final String TYPE_SYNC = "SYNC";
    public static final String TYPE_TAKEOVER = "TAKEOVER";
    public static final String TYPE_RELEASE = "RELEASE";

    private String deltaType;
    private Integer snakeIndex;
    private String ownerUserCode;
    private boolean alive;
    private String deathReason;
    private PointVO head;
    private List<PointVO> body;
    private Integer revealAllTimer;
    private Integer fogTimer;
    private Integer maxLength;
}