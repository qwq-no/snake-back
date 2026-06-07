package com.example.snake_back.service;

import com.example.snake_back.pojo.vo.FriendRequestListVO;

import java.util.List;

public interface FriendRequestService {

    void friendApply(Integer myUserCode, Integer otherUserCode);

    void handleFriendRequest(String requestId, String action);

    List<FriendRequestListVO> getFriendRequestList(Integer myUserCode);
}