package com.example.snake_back.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.snake_back.common.utils.JwtUtil;
import com.example.snake_back.common.utils.TokenUtil;
import com.example.snake_back.manager.SessionContextManager;
import com.example.snake_back.mapper.UserMapper;
import com.example.snake_back.pojo.dto.SessionContextDTO;
import com.example.snake_back.pojo.dto.UserLoginDTO;
import com.example.snake_back.pojo.dto.UserRegisterDTO;
import com.example.snake_back.pojo.entity.User;
import com.example.snake_back.service.RefreshTokenService;
import com.example.snake_back.service.UserService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 最小注册实现：检查用户名唯一 -> 写入 users 表
 */
@Service
public class UserServiceImpl implements UserService {
    private static final DateTimeFormatter DB_DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final RefreshTokenService refreshTokenService;
    private final JwtUtil jwtUtil;
    private final SessionContextManager sessionContextManager;
    public UserServiceImpl(UserMapper userMapper, RefreshTokenService refreshTokenService, JwtUtil jwtUtil,
                           SessionContextManager sessionContextManager) {
        this.userMapper = userMapper;
        this.refreshTokenService = refreshTokenService;
        this.jwtUtil = jwtUtil;
        this.sessionContextManager = sessionContextManager;
    }

    @Override
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public Map<String,Object> login(UserLoginDTO dto, String deviceInfo, String ip){
        Map<String,Object> claims = new HashMap<>();
        User exist = userMapper.selectOne(new QueryWrapper<User>().eq("username", dto.getUsername()));
        if (exist == null) {
            throw new IllegalArgumentException("用户名或密码错误");
        }

        String now = LocalDateTime.now().format(DB_DT);

        // 检查是否在锁定期
        if (exist.getLockoutUntil() != null && exist.getLockoutUntil().compareTo(now) > 0) {
            throw new IllegalArgumentException("输入次数太多，请稍后再输入");
        }

        // 锁已过期，自动清除
        if (exist.getLockoutUntil() != null && exist.getLockoutUntil().compareTo(now) <= 0) {
            exist.setFailedLoginCount(0);
            exist.setLockoutUntil(null);
            exist.setUpdatedAt(now);
            userMapper.updateById(exist);
        }

        if (dto.getPassword() == null || exist.getPasswordHash() == null
                || !passwordEncoder.matches(dto.getPassword(), exist.getPasswordHash())) {
            // 密码错误，累加次数
            int curCount = exist.getFailedLoginCount() == null ? 0 : exist.getFailedLoginCount();
            int newCount = curCount + 1;
            exist.setFailedLoginCount(newCount);
            if (newCount >= 5) {
                exist.setLockoutUntil(LocalDateTime.now().plusMinutes(5).format(DB_DT));
            }
            exist.setUpdatedAt(now);
            userMapper.updateById(exist);
            throw new IllegalArgumentException("用户名或密码错误");
        }

        // 登录成功，清除失败记录
        exist.setFailedLoginCount(0);
        exist.setLockoutUntil(null);
        String tokenPlain = refreshTokenService.createAndSaveRefreshToken(exist.getId(), deviceInfo, ip);
        exist.setLastLoginAt(now);
        exist.setUpdatedAt(now);
        userMapper.updateById(exist);

        claims.put("UserId", exist.getId());
        String token = jwtUtil.generateToken(claims);

        Map<String,Object> result = new HashMap<>();
        result.put("accessToken", token);
        result.put("refreshToken", tokenPlain);

        Map<String,Object> user = new HashMap<>();
        user.put("userCode", String.format("%06d", exist.getUserCode()));
        user.put("username", exist.getUsername());
        user.put("displayName", exist.getDisplayName());

        result.put("user", user);
        return result;
    }

    @Override
    @Transactional
    public void register(UserRegisterDTO dto) {
        // 简单唯一性检查（可扩展为更严格的并发检查）
        User exist = userMapper.selectOne(new QueryWrapper<User>().eq("username", dto.getUsername()));
        if (exist != null) {
            throw new IllegalArgumentException("username already exists");
        }
        String now = LocalDateTime.now().format(DB_DT);
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

    @Override
    @Transactional
    public int compareMaxLength(int length,String userId) {
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("id", userId));
        if (user == null) {throw new IllegalArgumentException("invalid user id");}
        int maxLength = user.getMaxLength();
        if (maxLength < length) {
            user.setMaxLength(length);
            userMapper.updateById(user);
            return length;
        }
        else {
            return maxLength;
        }
    }

    @Override
    @Transactional
    public void updateDisplayName(String userId, String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("display name cannot be empty");
        }
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("id", userId));
        if (user == null) {
            throw new IllegalArgumentException("invalid user id");
        }
        String trimmed = displayName.trim();
        user.setDisplayName(trimmed);
        user.setUpdatedAt(LocalDateTime.now().format(DB_DT));
        userMapper.updateById(user);

        // 同步更新内存中的 SessionContext，否则好友列表不会立即反映新名称
        String userCodeStr = String.format("%06d", user.getUserCode());
        String sessionId = sessionContextManager.getUserCodeToSessionIdMap().get(userCodeStr);
        if (sessionId != null) {
            SessionContextDTO ctx = sessionContextManager.getSessionContextMap().get(sessionId);
            if (ctx != null) {
                ctx.setNickname(trimmed);
            }
        }
    }

    @Override
    @Transactional
    public void updatePassword(String userId, String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("password cannot be empty");
        }
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("id", userId));
        if (user == null) {
            throw new IllegalArgumentException("invalid user id");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(LocalDateTime.now().format(DB_DT));
        user.setUpdatedAt(LocalDateTime.now().format(DB_DT));
        userMapper.updateById(user);
    }
}