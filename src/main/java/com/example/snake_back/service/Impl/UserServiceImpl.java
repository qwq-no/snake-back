package com.example.snake_back.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.snake_back.pojo.dto.UserRegisterDto;
import com.example.snake_back.mapper.UserMapper;
import com.example.snake_back.pojo.entity.User;
import com.example.snake_back.service.RefreshTokenService;
import com.example.snake_back.service.UserService;
import com.example.snake_back.common.utils.TokenUtil;
import com.example.snake_back.pojo.dto.UserLoginDto;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

/**
 * 最小注册实现：检查用户名唯一 -> 写入 users 表
 */
@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final RefreshTokenService refreshTokenService;

    public UserServiceImpl(UserMapper userMapper,RefreshTokenService refreshTokenService) {
        this.userMapper = userMapper;
        this.refreshTokenService = refreshTokenService;
    }

    @Override
    @Transactional
    public String login(UserLoginDto dto,String deviceInfo,String ip){
        User exist = userMapper.selectOne(new QueryWrapper<User>().eq("username", dto.getUsername()));
        if (exist == null) {
            throw new IllegalArgumentException("invalid username or password");
        }
        String passwordEncrypted = passwordEncoder.encode(dto.getPassword());
        if(passwordEncrypted == null || !passwordEncrypted.equals(dto.getPassword())){
            throw new IllegalArgumentException("invalid password");
        }
        String tokenPlain = refreshTokenService.createAndSaveRefreshToken(exist.getId(),deviceInfo,ip);
        String now = Instant.now().toString();
        exist.setLastLoginAt(now);
        exist.setUpdatedAt(now);
        userMapper.updateById(exist);
        return tokenPlain;
    }

    @Override
    @Transactional
    public void register(UserRegisterDto dto) {
        // 简单唯一性检查（可扩展为更严格的并发检查）
        User exist = userMapper.selectOne(new QueryWrapper<User>().eq("username", dto.getUsername()));
        if (exist != null) {
            throw new IllegalArgumentException("username already exists");
        }

        String now = Instant.now().toString();

        User user = new User();
        user.setId(TokenUtil.newUuid());
        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        user.setDisplayName(dto.getDisplayName());
        user.setIsActive(true);
        user.setFailedLoginCount(0);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        userMapper.insert(user);
    }
}