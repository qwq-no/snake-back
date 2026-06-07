package com.example.snake_back.pojo.dto;

import com.example.snake_back.pojo.vo.EmojiMessageVO;
import lombok.Data;

import java.util.*;
@Data
public class RoomState {
    private int roomCode;                          // 房间号
    private long countdownMin;                     // 倒计时，单位分
    private long countdownSecond;                  // 倒计时，单位秒
    private long gameStartTime;                    //游戏开始时间
    private String status;                         // waiting / playing

    private List<SnakeState> snakes = new ArrayList<>();               // 所有蛇：真人 + 人机
    private Map<String, Integer> userCodeToSnakeIndex = new HashMap<>();  //真人蛇userCode对应List的位置
    private Set<String> fruits = new HashSet<>();  // 水果位置，如 "x,y"

    private Set<String> speedUp = new HashSet<>();     // 道具 3
    private Set<String> speedDown = new HashSet<>();   // 道具 4
    private Set<String> revealAll = new HashSet<>();   // 道具 5
    private Set<String> fog = new HashSet<>();         // 道具 6

    private Deque<EmojiMessageVO> roomEmojis = new ArrayDeque<>();  //表情包

    private int[][] map = new int[102][102];       // 地图，0 表示空地
}