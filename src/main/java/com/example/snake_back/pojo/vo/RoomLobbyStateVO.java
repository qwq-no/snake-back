package com.example.snake_back.pojo.vo;

import lombok.Data;

import java.util.Set;

@Data
public class RoomLobbyStateVO {
    private Integer roomCode;
    private String status;
    private Set<String> members;
    private Set<String> readyUsers;
}