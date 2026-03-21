package com.example.snake_back.service.Impl;

import com.example.snake_back.mapper.RefreshTokenMapper;
import com.example.snake_back.pojo.entity.RefreshToken;
import com.example.snake_back.common.utils.TokenUtil;
import com.example.snake_back.service.RefreshTokenService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * RefreshTokenServiceImpl - 增加 validateAndRotate 与 revoke 功能
 */
@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenMapper refreshTokenMapper;

    // refresh 有效期示例：30 天
    private final long refreshDays = 30;

    public RefreshTokenServiceImpl(RefreshTokenMapper refreshTokenMapper) {
        this.refreshTokenMapper = refreshTokenMapper;
    }

    /**
     * 为指定用户创建 refresh token（返回明文 token 给调用方）
     */
    @Override
    public String createAndSaveRefreshToken(String userId, String deviceInfo, String ip) {
        String tokenPlain = TokenUtil.generateTokenPlain(32); // 32 bytes -> base64 string
        String tokenHash = TokenUtil.sha256Hex(tokenPlain);

        RefreshToken rt = new RefreshToken();
        rt.setId(TokenUtil.newUuid());
        rt.setUserId(userId);
        rt.setTokenHash(tokenHash);
        rt.setIssuedAt(Instant.now().toString());
        rt.setExpiresAt(Instant.now().plus(refreshDays, ChronoUnit.DAYS).toString());
        rt.setRevoked(false);
        rt.setDeviceInfo(deviceInfo);
        rt.setIp(ip);
        rt.setCreatedAt(Instant.now().toString());
        refreshTokenMapper.insert(rt);

        return tokenPlain;
    }

    // 验证 refresh token（简单版本，未锁）
    @Override
    public RefreshToken validateRefreshToken(String tokenPlain) {
        String tokenHash = TokenUtil.sha256Hex(tokenPlain);
        RefreshToken rt = refreshTokenMapper.selectByTokenHash(tokenHash);
        if (rt == null) return null;
        if (Boolean.TRUE.equals(rt.getRevoked())) return null;
        // 检查过期
        try {
            if (rt.getExpiresAt() != null && Instant.parse(rt.getExpiresAt()).isBefore(Instant.now())) {
                return null;
            }
        } catch (Exception ignored) {}
        return rt;
    }

    /**
     * 验证并旋转 refresh token（事务内）。成功返回新的 tokenPlain；失败返回 null。
     * 需要 Mapper 提供 selectByTokenHashForUpdate(tokenHash)，在事务中使用 FOR UPDATE。
     */
    @Override
    @Transactional
    public String validateAndRotate(String oldPlain, String ip, String deviceInfo) {
        if (oldPlain == null) return null;
        String oldHash = TokenUtil.sha256Hex(oldPlain);

        // 锁定行，防止并发同时旋转
        RefreshToken oldRec = refreshTokenMapper.selectByTokenHashForUpdate(oldHash);
        if (oldRec == null) {
            return null;
        }

        // 已撤销 -> 重放/异常
        if (Boolean.TRUE.equals(oldRec.getRevoked())) {
            // 可在此记录日志/告警：收到已撤销的 token（可能为重放）
            return null;
        }

        // 检查过期
        try {
            if (oldRec.getExpiresAt() != null && Instant.parse(oldRec.getExpiresAt()).isBefore(Instant.now())) {
                // 已过期
                return null;
            }
        } catch (Exception ignored) {}

        // 标记旧记录为 revoked 并设置 lastUsedAt / revokedAt（稍后设置 replacedBy）
        oldRec.setRevoked(true);
        oldRec.setRevokedAt(Instant.now().toString());
        oldRec.setLastUsedAt(Instant.now().toString());
        refreshTokenMapper.updateById(oldRec);

        // 生成新的 token 并插入（设置 previous/replaced 关联）
        String newPlain = TokenUtil.generateTokenPlain(32);
        String newHash = TokenUtil.sha256Hex(newPlain);
        RefreshToken newRec = new RefreshToken();
        String newId = TokenUtil.newUuid();
        newRec.setId(newId);
        newRec.setUserId(oldRec.getUserId());
        newRec.setTokenHash(newHash);
        newRec.setIssuedAt(Instant.now().toString());
        newRec.setExpiresAt(Instant.now().plus(refreshDays, ChronoUnit.DAYS).toString());
        newRec.setRevoked(false);
        newRec.setDeviceInfo(deviceInfo);
        newRec.setIp(ip);
        newRec.setReplacedBy(oldRec.getId());
        newRec.setCreatedAt(Instant.now().toString());
        refreshTokenMapper.insert(newRec);

        // 回填旧记录的 replacedBy 字段（可选）
        oldRec.setReplacedBy(newRec.getId());
        oldRec.setUpdatedAt(Instant.now().toString());
        refreshTokenMapper.updateById(oldRec);

        return newPlain;
    }

    /**
     * 根据明文撤销（logout 或发现滥用时），若成功返回 true。
     */
    @Override
    @Transactional
    public boolean revokeByPlain(String tokenPlain) {
        if (tokenPlain == null) return false;
        String tokenHash = TokenUtil.sha256Hex(tokenPlain);
        RefreshToken rec = refreshTokenMapper.selectByTokenHash(tokenHash);
        if (rec == null) return false;

        // 如果已被撤销，不重复操作
        if (Boolean.TRUE.equals(rec.getRevoked())) {
            return false;
        }

        rec.setRevoked(true);
        rec.setRevokedAt(Instant.now().toString());
        rec.setUpdatedAt(Instant.now().toString());
        refreshTokenMapper.updateById(rec);
        return true;
    }

    /**
     * 批量撤销某个用户的所有 refresh（用于可疑活动时强制登出）
     */
    @Override
    @Transactional
    public int revokeAllForUser(String userId) {
        return refreshTokenMapper.revokeAllByUserId(userId, Instant.now().toString());
    }
}