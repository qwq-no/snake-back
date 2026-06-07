package com.example.snake_back.controller;

import com.example.snake_back.common.result.Result;
import com.example.snake_back.pojo.vo.FriendListVO;
import com.example.snake_back.service.Impl.FriendshipServiceImpl;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/friendship")
public class FriendshipController {

    private final FriendshipServiceImpl friendshipService;
    public FriendshipController(FriendshipServiceImpl friendshipService) {
        this.friendshipService = friendshipService;
    }

    @GetMapping("/list")
    public Result<List<FriendListVO>> getFriendList(@RequestParam Integer myUserCode) {
        try{
            return Result.success(friendshipService.getFriendList(myUserCode));
        }catch (Exception e){
            return Result.error(e.getMessage());
        }
    }
}