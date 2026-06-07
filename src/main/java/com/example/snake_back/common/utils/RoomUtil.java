package com.example.snake_back.common.utils;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.snake_back.mapper.UserMapper;
import com.example.snake_back.pojo.dto.Node;
import com.example.snake_back.pojo.dto.RoomState;
import com.example.snake_back.pojo.dto.SnakeState;
import com.example.snake_back.pojo.entity.User;
import org.springframework.stereotype.Component;

@Component
public class RoomUtil {
    public void resetRoom(RoomState roomState) {
        roomState.setStatus("waiting");
        roomState.setCountdownMin(10);
        roomState.setCountdownSecond(0);
        roomState.setGameStartTime(0L);

        roomState.getUserCodeToSnakeIndex().clear();
        roomState.getSnakes().clear();
        roomState.getFruits().clear();
        roomState.getSpeedUp().clear();
        roomState.getSpeedDown().clear();
        roomState.getRevealAll().clear();
        roomState.getFog().clear();

        int[][] map = roomState.getMap();
        for (int i = 0; i < 102; i++) {
            for (int j = 0; j < 102; j++) {
                map[i][j] = 0;
            }
        }
    }
    public void refreshSnake(RoomState roomState, SnakeState snake) {
        int[][] map = roomState.getMap();
        int[] location = randomPlace(map);
        int x = location[0];
        int y = location[1];

        Node head = new Node();
        head.setX(x);
        head.setY(y);

        snake.getBody().clear();
        snake.getBody().add(head);

        snake.setDirection(null);
        snake.setDirectionNext(null);
        snake.setRespawnTimer(0);
        snake.setChangeDirTimer(0);
        snake.setAlive(true);
        snake.setMoveCounter(0);
        snake.setMoveInterval(2);
        snake.getPropsTimer().setSpeedUp(0);
        snake.getPropsTimer().setSpeedDown(0);
        snake.getPropsTimer().setRevealAll(0);
        snake.getPropsTimer().setFog(0);
        snake.setEmojiTimer(0);

        map[x][y] = 1;
    }
    public int[] randomPlace(int[][] map) {
        for (int attempts = 0; attempts < 5000; attempts++) {
            int x = (int) (Math.random() * 100) + 1;
            int y = (int) (Math.random() * 100) + 1;
            if (map[x][y] == 0) {
                return new int[]{x, y};
            }
        }

        // Deterministic fallback prevents random probing from blocking game logic when map is crowded.
        for (int x = 1; x <= 100; x++) {
            for (int y = 1; y <= 100; y++) {
                if (map[x][y] == 0) {
                    return new int[]{x, y};
                }
            }
        }

        throw new IllegalStateException("No available empty cell in map for randomPlace");
    }
    public static int updateAndGetMaxLength(int currentLength, String userCodeStr, UserMapper userMapper) {
        // userCode 前端存储的是零填充字符串（如 "000123"），数据库 user_code 是 INT，
        // 必须转成 Integer 再查询，否则 MyBatis-Plus 用 String 参数查 INT 列会匹配失败
        Integer userCode;
        try {
            userCode = Integer.parseInt(userCodeStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid user code: " + userCodeStr);
        }
        User user = userMapper.selectOne(
                new QueryWrapper<User>().eq("user_code", userCode)
        );
        if (user == null) {
            throw new IllegalArgumentException("invalid user code: " + userCode);
        }

        int dbMax = user.getMaxLength() == null ? 0 : user.getMaxLength();
        if (currentLength > dbMax) {
            user.setMaxLength(currentLength);
            userMapper.updateById(user);
            return currentLength;
        }
        return dbMax;
    }
}
