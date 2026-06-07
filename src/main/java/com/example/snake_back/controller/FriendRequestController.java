package com.example.snake_back.controller;

import com.example.snake_back.common.result.Result;
import com.example.snake_back.pojo.dto.FriendApplyDTO;
import com.example.snake_back.pojo.dto.FriendRequestHandleDTO;
import com.example.snake_back.service.FriendRequestService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/friendRequest")
public class FriendRequestController {

    private final FriendRequestService friendRequestService;
    public FriendRequestController(FriendRequestService friendRequestService) {
        this.friendRequestService = friendRequestService;
    }

    // 发起好友申请
    @PostMapping("/friendApply")
    public Result<String> friendApply(@RequestBody FriendApplyDTO dto) {
        try {
            friendRequestService.friendApply(dto.getMyUserCode(), dto.getOtherUserCode());
            return Result.success();
        }catch (Exception e){
            return Result.error(e.getMessage());
        }
    }

    // 接受/拒绝好友申请
    @PostMapping("/handleFriendRequest")
    public Result<String> handleFriendRequest(@RequestBody FriendRequestHandleDTO dto) {
        try {
            friendRequestService.handleFriendRequest(dto.getRequestId(), dto.getAction());
            return Result.success();
        }catch (Exception e){
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/list")
    public Result<Map<String,Object>> getFriendRequestList(@RequestParam Integer myUserCode) {
        try {
            Map<String, Object> map = new HashMap<>();
            map.put("FriendRequestList", friendRequestService.getFriendRequestList(myUserCode));
            return Result.success(map);
        }catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}