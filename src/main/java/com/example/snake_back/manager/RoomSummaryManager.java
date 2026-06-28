package com.example.snake_back.manager;

import com.example.snake_back.pojo.vo.RoomSummaryVO;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class RoomSummaryManager {

    @Value("${app.game.room-count:10}")
    private int roomCount;

    private RoomSummaryVO[] roomSummaries;

    @PostConstruct
    public void initRooms() {
        roomSummaries = new RoomSummaryVO[roomCount + 1];
        for (int i = 1; i <= roomCount; i++) {
            RoomSummaryVO vo = new RoomSummaryVO();
            vo.setRoomCode(i);
            vo.setPlayerCount(0);
            vo.setStartTime(0L);
            vo.setUserCodes(new String[0]);
            vo.setStatus("waiting");
            roomSummaries[i] = vo;
        }
    }

    public RoomSummaryVO[] getAllRooms() {
        return roomSummaries;
    }

    public RoomSummaryVO getRoom(int roomCode) {
        checkRoomCode(roomCode);
        return roomSummaries[roomCode];
    }

    public void joinRoom(int roomCode, String userCode) {
        checkRoomCode(roomCode);
        applyMemberChange(roomCode, userCode, true);
    }

    public void leaveRoom(int roomCode, String userCode) {
        checkRoomCode(roomCode);
        applyMemberChange(roomCode, userCode, false);
    }

    public RoomSummaryVO applyMemberChange(int roomCode, String userCode, boolean joined) {
        checkRoomCode(roomCode);

        RoomSummaryVO vo = roomSummaries[roomCode];
        if (vo == null) {
            return null;
        }

        String[] oldUserCodes = vo.getUserCodes();
        if (oldUserCodes == null) {
            oldUserCodes = new String[0];
        }

        if (joined) {
            for (String code : oldUserCodes) {
                if (userCode.equals(code)) {
                    return vo;
                }
            }

            String[] newUserCodes = Arrays.copyOf(oldUserCodes, oldUserCodes.length + 1);
            newUserCodes[oldUserCodes.length] = userCode;
            vo.setUserCodes(newUserCodes);
            vo.setPlayerCount(newUserCodes.length);
            return vo;
        }

        if (oldUserCodes.length == 0) {
            return vo;
        }

        String[] newUserCodes = Arrays.stream(oldUserCodes)
                .filter(code -> !userCode.equals(code))
                .toArray(String[]::new);

        vo.setUserCodes(newUserCodes);
        vo.setPlayerCount(newUserCodes.length);
        if (newUserCodes.length == 0) {
            vo.setStatus("waiting");
            vo.setStartTime(0L);
        }
        return vo;
    }

    public void startGame(int roomCode, long startTime) {
        checkRoomCode(roomCode);

        RoomSummaryVO vo = roomSummaries[roomCode];
        if (vo == null) {
            return;
        }

        vo.setStartTime(startTime);
        vo.setStatus("playing");
    }

    public void resetRoom(int roomCode) {
        checkRoomCode(roomCode);

        RoomSummaryVO vo = roomSummaries[roomCode];
        if (vo == null) {
            return;
        }

        vo.setPlayerCount(0);
        vo.setStartTime(0L);
        vo.setUserCodes(new String[0]);
        vo.setStatus("waiting");
    }

    public void setStatus(int roomCode, String status) {
        checkRoomCode(roomCode);

        RoomSummaryVO vo = roomSummaries[roomCode];
        if (vo == null) {
            return;
        }

        vo.setStatus(status);
    }

    public void setStartTime(int roomCode, long startTime) {
        checkRoomCode(roomCode);

        RoomSummaryVO vo = roomSummaries[roomCode];
        if (vo == null) {
            return;
        }

        vo.setStartTime(startTime);
    }

    public void setPlayerCount(int roomCode, int playerCount) {
        checkRoomCode(roomCode);

        RoomSummaryVO vo = roomSummaries[roomCode];
        if (vo == null) {
            return;
        }

        vo.setPlayerCount(playerCount);
    }

    public void setUserCodes(int roomCode, String[] userCodes) {
        checkRoomCode(roomCode);

        RoomSummaryVO vo = roomSummaries[roomCode];
        if (vo == null) {
            return;
        }

        vo.setUserCodes(userCodes != null ? userCodes : new String[0]);
        vo.setPlayerCount(vo.getUserCodes().length);
    }

    private void checkRoomCode(int roomCode) {
        if (roomCode < 1 || roomCode > roomCount) {
            throw new IllegalArgumentException("roomCode must be between 1 and " + roomCount);
        }
    }
}