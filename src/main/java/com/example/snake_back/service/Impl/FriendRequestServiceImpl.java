package com.example.snake_back.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.snake_back.common.utils.TokenUtil;
import com.example.snake_back.mapper.FriendRequestMapper;
import com.example.snake_back.mapper.FriendshipMapper;
import com.example.snake_back.mapper.UserMapper;
import com.example.snake_back.pojo.entity.FriendRequest;
import com.example.snake_back.pojo.entity.Friendship;
import com.example.snake_back.pojo.entity.User;
import com.example.snake_back.pojo.vo.FriendRequestListVO;
import com.example.snake_back.service.FriendRequestService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class FriendRequestServiceImpl implements FriendRequestService {

    private final FriendRequestMapper friendRequestMapper;
    private final FriendshipMapper friendshipMapper;
    private final UserMapper userMapper;
    public FriendRequestServiceImpl(FriendRequestMapper friendRequestMapper, FriendshipMapper friendshipMapper, UserMapper userMapper) {
        this.friendRequestMapper = friendRequestMapper;
        this.friendshipMapper = friendshipMapper;
        this.userMapper = userMapper;
    }

    @Override
    public void friendApply(Integer myUserCode, Integer otherUserCode) {
        if (myUserCode == null || otherUserCode == null) {
            throw new RuntimeException("userCode不能为空");
        }
        if (myUserCode.equals(otherUserCode)) {
            throw new RuntimeException("不能添加自己为好友");
        }

        User fromUser = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUserCode, myUserCode));
        User toUser = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUserCode, otherUserCode));

        if (fromUser == null || toUser == null) {
            throw new RuntimeException("用户不存在");
        }

        // 检查是否已经是好友
        LambdaQueryWrapper<Friendship> friendshipQuery = new LambdaQueryWrapper<>();
        friendshipQuery.and(wrapper -> wrapper
                .eq(Friendship::getUserCode1, myUserCode).eq(Friendship::getUserCode2, otherUserCode)
                .or()
                .eq(Friendship::getUserCode1, otherUserCode).eq(Friendship::getUserCode2, myUserCode));
        Friendship existingFriendship = friendshipMapper.selectOne(friendshipQuery);
        if (existingFriendship != null) {
            throw new RuntimeException("已经是好友");
        }

        // 检查是否已有待处理申请
        LambdaQueryWrapper<FriendRequest> requestQuery = new LambdaQueryWrapper<>();
        requestQuery.eq(FriendRequest::getFromUserCode, myUserCode)
                .eq(FriendRequest::getToUserCode, otherUserCode)
                .eq(FriendRequest::getStatus, "pending");
        FriendRequest existingRequest = friendRequestMapper.selectOne(requestQuery);
        if (existingRequest != null) {
            throw new RuntimeException("已经发送过好友申请");
        }

        FriendRequest friendRequest = new FriendRequest();
        friendRequest.setId(TokenUtil.newUuid());
        friendRequest.setFromUserCode(myUserCode);
        friendRequest.setToUserCode(otherUserCode);
        friendRequest.setStatus("pending");

        friendRequestMapper.insert(friendRequest);
    }

    @Override
    public void handleFriendRequest(String requestId, String action) {
        FriendRequest request = friendRequestMapper.selectById(requestId);
        if (request == null) {
            throw new RuntimeException("好友申请不存在");
        }

        if ("accept".equalsIgnoreCase(action)) {
            request.setStatus("accepted");
            friendRequestMapper.updateById(request);

            Friendship friendship = new Friendship();
            friendship.setId(TokenUtil.newUuid());

            Integer userCode1 = request.getFromUserCode();
            Integer userCode2 = request.getToUserCode();

            // 保证顺序固定，避免重复
            if (userCode1 <= userCode2) {
                friendship.setUserCode1(userCode1);
                friendship.setUserCode2(userCode2);
            } else {
                friendship.setUserCode1(userCode2);
                friendship.setUserCode2(userCode1);
            }

            friendshipMapper.insert(friendship);
        } else if ("reject".equalsIgnoreCase(action)) {
            friendRequestMapper.deleteById(requestId);
        } else {
            throw new RuntimeException("非法操作");
        }
    }

    @Override
    public List<FriendRequestListVO> getFriendRequestList(Integer myUserCode) {
        if (myUserCode == null) {
            throw new RuntimeException("userCode不能为空");
        }

        LambdaQueryWrapper<FriendRequest> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FriendRequest::getToUserCode, myUserCode)
                .eq(FriendRequest::getStatus, "pending");

        List<FriendRequest> requestList = friendRequestMapper.selectList(queryWrapper);
        List<FriendRequestListVO> result = new ArrayList<>();

        for (FriendRequest request : requestList) {
            User fromUser = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getUserCode, request.getFromUserCode()));

            FriendRequestListVO vo = new FriendRequestListVO();
            vo.setRequestId(request.getId());
            vo.setFromUserCode(request.getFromUserCode());
            vo.setFromUserName(fromUser == null ? null : fromUser.getUsername());
            result.add(vo);
        }

        return result;
    }
}