package com.example.snake_back.service;

import com.example.snake_back.pojo.vo.FriendListVO;

import java.util.List;

public interface FriendshipService {
    List<FriendListVO> getFriendList(Integer myUserCode);
}