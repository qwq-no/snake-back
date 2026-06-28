package com.example.snake_back.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.snake_back.mapper.FriendshipMapper;
import com.example.snake_back.mapper.UserMapper;
import com.example.snake_back.pojo.entity.Friendship;
import com.example.snake_back.pojo.entity.User;
import com.example.snake_back.pojo.vo.FriendListVO;
import com.example.snake_back.service.FriendshipService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class FriendshipServiceImpl implements FriendshipService {

    @Autowired
    private FriendshipMapper friendshipMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    public List<FriendListVO> getFriendList(Integer myUserCode) {
        if (myUserCode == null) {
            throw new RuntimeException("userCode不能为空");
        }

        LambdaQueryWrapper<Friendship> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.and(wrapper -> wrapper
                .eq(Friendship::getUserCode1, myUserCode)
                .or()
                .eq(Friendship::getUserCode2, myUserCode));

        List<Friendship> friendships = friendshipMapper.selectList(queryWrapper);
        List<FriendListVO> result = new ArrayList<>();

        for (Friendship friendship : friendships) {
            Integer friendUserCode;
            if (friendship.getUserCode1().equals(myUserCode)) {
                friendUserCode = friendship.getUserCode2();
            } else {
                friendUserCode = friendship.getUserCode1();
            }

            User friendUser = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getUserCode, friendUserCode));

            FriendListVO vo = new FriendListVO();
            vo.setUserCode(friendUserCode);
            vo.setUserName(friendUser == null ? null :
                (friendUser.getDisplayName() != null ? friendUser.getDisplayName() : friendUser.getUsername()));
            result.add(vo);
        }

        return result;
    }

    @Override
    public void removeFriend(Integer myUserCode, Integer friendUserCode) {
        if (myUserCode == null || friendUserCode == null) {
            throw new RuntimeException("userCode不能为空");
        }

        LambdaQueryWrapper<Friendship> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.and(wrapper -> wrapper
                .eq(Friendship::getUserCode1, myUserCode)
                .eq(Friendship::getUserCode2, friendUserCode)
                .or()
                .eq(Friendship::getUserCode1, friendUserCode)
                .eq(Friendship::getUserCode2, myUserCode));

        Friendship friendship = friendshipMapper.selectOne(queryWrapper);
        if (friendship == null) {
            throw new RuntimeException("好友关系不存在");
        }

        friendshipMapper.deleteById(friendship.getId());
    }
}