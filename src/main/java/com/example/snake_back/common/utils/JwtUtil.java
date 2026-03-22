package com.example.snake_back.common.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * JWT工具类（适配 JJWT 0.11.5 新 API，解决弃用警告）
 */
@Component // 必须加，让Spring扫描创建Bean
public class JwtUtil {
    // 默认秘钥（长度必须≥256位/32个字符，否则新版JJWT会报错）
    private static final String DEFAULT_SECRET_KEY = "sky1234567890sky1234567890sky1234567890";
    // 默认过期时间：30分钟（毫秒）
    private static final long DEFAULT_TTL_MILLIS = 30 * 60 * 1000;

    /**
     * 适配你调用的 generateToken 方法（无弃用警告）
     */
    public String generateToken(Map<String, Object> claims) {
        return createJWT(DEFAULT_SECRET_KEY, DEFAULT_TTL_MILLIS, claims);
    }

    /**
     * 生成JWT（新版API写法）
     */
    public static String createJWT(String secretKey, long ttlMillis, Map<String, Object> claims) {
        // 新版：用 Keys.hmacShaKeyFor 生成密钥（替代旧的 signWith 直接传字节数组）
        return Jwts.builder()
                .setClaims(claims) // 设置自定义载荷
                .setExpiration(new Date(System.currentTimeMillis() + ttlMillis)) // 设置过期时间
                // 新版签名方式：指定算法 + 密钥（解决 signWith 弃用）
                .signWith(Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    /**
     * 解析JWT（新版API写法，解决 parser() 弃用）
     */
    public static Claims parseJWT(String secretKey, String token) {
        // 新版：用 parserBuilder() 替代 parser()
        return Jwts.parserBuilder()
                // 新版：用 setSigningKey 接收 Key 对象（替代旧的字节数组）
                .setSigningKey(Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8)))
                .build() // 构建解析器
                .parseClaimsJws(token) // 解析Token
                .getBody(); // 获取载荷
    }

    /**
     * 简化解析方法（适配业务调用）
     */
    public Claims parseToken(String token) {
        return parseJWT(DEFAULT_SECRET_KEY, token);
    }

    /**
     * 从Token中解析出指定key的Long类型值（比如employeeId）
     * @param token JWT令牌
     * @param key 载荷中的key（如"employeeId"）
     * @return 解析出的Long值
     */
    public Long getLongFromToken(String token, String key) {
        Claims claims = parseToken(token);
        // 从载荷中获取指定key的Long值，不存在则抛异常
        return claims.get(key, Long.class);
    }

    /**
     * 从Token中解析出指定key的任意类型值（通用方法）
     * @param token JWT令牌
     * @param key 载荷中的key
     * @param clazz 要转换的类型
     * @return 解析出的对应类型值
     */
    public <T> T getValueFromToken(String token, String key, Class<T> clazz) {
        Claims claims = parseToken(token);
        return claims.get(key, clazz);
    }

    // 🌟 快捷方法：直接解析员工ID（适配你的业务）
    public Long getEmployeeIdFromToken(String token) {
        // 固定key为"employeeId"，和你登录时存入的一致
        return getLongFromToken(token, "employeeId");
    }
}