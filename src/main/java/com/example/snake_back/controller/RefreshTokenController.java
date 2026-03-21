package com.example.snake_back.controller;

import com.example.snake_back.service.RefreshTokenService;
import com.example.snake_back.common.result.Result;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.WebUtils;

/**
 * 简单示例 Controller：登录后设置 HttpOnly refresh cookie
 * （示例只演示 refresh cookie 的设置，登录验证密码逻辑省略）
 */
@RestController
@RequestMapping("/api/refresh")
public class RefreshTokenController {

    private final RefreshTokenService refreshTokenService;

    public RefreshTokenController(RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> refresh(HttpServletRequest request, HttpServletResponse response) {
        Cookie cookie = WebUtils.getCookie(request, "refresh_token");
        if (cookie == null) {
            return ResponseEntity.ok().body(Result.error("401,不存在cookie"));
        }
        String oldPlain = cookie.getValue();

        // 验证并旋转：假设 refreshTokenService.validateAndRotate(oldPlain, ip, device) 返回 newPlain 或 null
        String newPlain = refreshTokenService.validateAndRotate(oldPlain, request.getRemoteAddr(), "web");
        if (newPlain == null) {
            // 无效或被撤销
            return ResponseEntity.ok().body(Result.error("401,cookie无效"));
        }

        // 写入新的 refresh cookie（覆盖旧的）
        ResponseCookie newCookie = ResponseCookie.from("refresh_token", newPlain)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(30L * 24 * 3600)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, newCookie.toString());

        return ResponseEntity.ok().body(Result.success());
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        Cookie cookie = WebUtils.getCookie(request, "refresh_token");
        if (cookie != null) {
            refreshTokenService.revokeByPlain(cookie.getValue()); // 标记 DB 中的 token 为 revoked
        }
        // 清除 cookie（与设置 cookie 时保持相同 path/domain）
        ResponseCookie expired = ResponseCookie.from("refresh_token", "")
                .httpOnly(true).secure(true).path("/").maxAge(0).sameSite("Lax").build();
        response.addHeader(HttpHeaders.SET_COOKIE, expired.toString());
        return ResponseEntity.ok().body(Result.success());
    }
}