package com.example.snake_back.controller;


import com.example.snake_back.pojo.vo.RoomSummaryVO;
import com.example.snake_back.service.SelectService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/room")
public class RoomController {

    private final SelectService selectService;

    public RoomController(SelectService selectService) {
        this.selectService = selectService;
    }

    /**
     * 获取大厅房间列表
     */
    @PostMapping("/list")
    public RoomSummaryVO[] listRooms() {
        return selectService.getRoomSummaries();
    }
}