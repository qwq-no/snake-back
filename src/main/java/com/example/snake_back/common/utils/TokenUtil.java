package com.example.snake_back.common.utils;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * 简单工具：生成随机 token 明文 (URL-safe Base64) 与计算 SHA-256 hex（用于存 DB）
 */
public class TokenUtil {

    private static final SecureRandom secureRandom = new SecureRandom();

    public static String generateTokenPlain(int rawBytesLength) {
        byte[] bytes = new byte[rawBytesLength];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // 返回 SHA-256 的 hex 字符串（小写）
    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String newUuid() {
        return UUID.randomUUID().toString();
    }
}