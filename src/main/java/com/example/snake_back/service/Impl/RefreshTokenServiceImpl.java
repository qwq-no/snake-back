package com.example.snake_back.service.Impl;

import com.example.snake_back.common.utils.JwtUtil;
import com.example.snake_back.common.utils.TokenUtil;
import com.example.snake_back.mapper.RefreshTokenMapper;
import com.example.snake_back.mapper.UserMapper;
import com.example.snake_back.pojo.entity.RefreshToken;
import com.example.snake_back.pojo.entity.User;
import com.example.snake_back.service.RefreshTokenService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * RefreshTokenServiceImpl - 增加 validateAndRotate 与 revoke 功能
 */
@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final DateTimeFormatter DB_DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final JwtUtil jwtUtil;
    private final RefreshTokenMapper refreshTokenMapper;
    private final UserMapper userMapper;

    // refresh 有效期示例：30 天
    private final long refreshDays = 30;

    public RefreshTokenServiceImpl(RefreshTokenMapper refreshTokenMapper, JwtUtil jwtUtil, UserMapper userMapper) {
        this.refreshTokenMapper = refreshTokenMapper;
        this.jwtUtil = jwtUtil;
        this.userMapper = userMapper;
    }

    /**
     * 为指定用户创建 refresh token（返回明文 token 给调用方）
     */
    @Override
    public String createAndSaveRefreshToken(String userId, String deviceInfo, String ip) {
        String tokenPlain = TokenUtil.generateTokenPlain(32);
        String tokenHash = TokenUtil.sha256Hex(tokenPlain);

        String now = nowStr();
        String expiresAt = nowPlusDaysStr(refreshDays);

        RefreshToken rt = new RefreshToken();
        rt.setId(TokenUtil.newUuid());
        rt.setUserId(userId);
        rt.setTokenHash(tokenHash);
        rt.setIssuedAt(now);
        rt.setExpiresAt(expiresAt);
        rt.setRevoked(false);
        rt.setDeviceInfo(deviceInfo);
        rt.setIp(ip);
        rt.setCreatedAt(now);
        rt.setUpdatedAt(now);

        refreshTokenMapper.insert(rt);
        return tokenPlain;
    }

    /**
     * 验证 refresh token（简单版本，未锁）
     */
    @Override
    public RefreshToken validateRefreshToken(String tokenPlain) {
        if (tokenPlain == null || tokenPlain.isBlank()) return null;

        String tokenHash = TokenUtil.sha256Hex(tokenPlain);
        RefreshToken rt = refreshTokenMapper.selectByTokenHash(tokenHash);
        if (rt == null) return null;
        if (Boolean.TRUE.equals(rt.getRevoked())) return null;

        // 检查过期（DB 字符串 -> LocalDateTime）
        if (isExpired(rt.getExpiresAt())) return null;

        return rt;
    }

    /**
     * 验证并旋转 refresh token事务内）。成功返回新的 tokenPlain；失败返回 null。
     */
    @Override
    @Transactional
    public Map<String,Object> validateAndRotate(String oldPlain, String ip, String deviceInfo) {
        if (oldPlain == null || oldPlain.isBlank()) return null;

        String oldHash = TokenUtil.sha256Hex(oldPlain);
        RefreshToken oldRec = refreshTokenMapper.selectByTokenHashForUpdate(oldHash);
        if (oldRec == null) return null;
        if (Boolean.TRUE.equals(oldRec.getRevoked())) return null;
        if (isExpired(oldRec.getExpiresAt())) return null;

        String now = nowStr();

        // 1) 生成新 token
        Map<String,Object> claims = new HashMap<>();
        String newId = TokenUtil.newUuid();
        String newPlain = TokenUtil.generateTokenPlain(32);
        String newHash = TokenUtil.sha256Hex(newPlain);
        claims.put("UserId", oldRec.getUserId());
        String token = jwtUtil.generateToken(claims);

        Map<String,Object> result = new HashMap<>();
        Map<String,Object> user = new HashMap<>();
        String userId = oldRec.getUserId();
        User exist = userMapper.selectById(userId);
        result.put("accessToken", token);
        result.put("refreshToken", newPlain);
        user.put("userCode", String.format("%06d",exist.getUserCode()));
        user.put("username", exist.getUsername());
        user.put("displayName", exist.getDisplayName());
        result.put("user", user);

        // 2) 插入新 refresh
        RefreshToken newRec = new RefreshToken();
        newRec.setId(newId);
        newRec.setUserId(oldRec.getUserId());
        newRec.setTokenHash(newHash);
        newRec.setIssuedAt(now);
        newRec.setExpiresAt(nowPlusDaysStr(refreshDays));
        newRec.setRevoked(false);
        newRec.setDeviceInfo(deviceInfo);
        newRec.setIp(ip);
        newRec.setCreatedAt(now);
        newRec.setUpdatedAt(now);
        newRec.setReplacedBy(null);
        refreshTokenMapper.insert(newRec);

        // 3) 更新旧 refresh
        oldRec.setRevoked(true);
        oldRec.setRevokedAt(now);
        oldRec.setLastUsedAt(now);
        oldRec.setReplacedBy(newId);
        oldRec.setUpdatedAt(now);
        refreshTokenMapper.updateById(oldRec);

        return result;
    }

    /**
     * 根据明文撤销（logout 或发现滥用时），若成功返回 true。
     */
    @Override
    @Transactional
    public boolean revokeByPlain(String tokenPlain) {
        if (tokenPlain == null || tokenPlain.isBlank()) return false;

        String tokenHash = TokenUtil.sha256Hex(tokenPlain);
        RefreshToken rec = refreshTokenMapper.selectByTokenHash(tokenHash);
        if (rec == null) return false;
        if (Boolean.TRUE.equals(rec.getRevoked())) return false;    //避免rec的revoked为空报错

        String now = nowStr();
        rec.setRevoked(true);
        rec.setRevokedAt(now);
        rec.setUpdatedAt(now);

        refreshTokenMapper.updateById(rec);
        return true;
    }

    /**
     * 批量撤销某个用户的所有 refresh（用于可疑活动时强制登出）
     */
    @Override
    @Transactional
    public int revokeAllForUser(String userId) {
        return refreshTokenMapper.revokeAllByUserId(userId, nowStr());
    }

    // ----------------- private helpers -----------------

    private String nowStr() {
        return LocalDateTime.now().format(DB_DT);
    }

    private String nowPlusDaysStr(long days) {
        return LocalDateTime.now().plusDays(days).format(DB_DT);
    }

    private boolean isExpired(String expiresAt) {
        if (expiresAt == null || expiresAt.isBlank()) return true;
        try {
            LocalDateTime exp = LocalDateTime.parse(expiresAt, DB_DT);
            return exp.isBefore(LocalDateTime.now());
        } catch (Exception e) {
            // 格式异常当作无效 token
            return true;
        }
    }
}