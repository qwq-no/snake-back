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

import java.util.HashMap;
import java.util.Map;

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

        Map<String,Object> loginData = refreshTokenService.validateAndRotate(oldPlain, request.getRemoteAddr(), "web");
        if (loginData == null) {
            return ResponseEntity.ok().body(Result.error("401,cookie无效"));
        }

        String newPlain = loginData.get("refreshToken").toString();

        // 写入新的 refresh cookie
        ResponseCookie newCookie = ResponseCookie.from("refresh_token", newPlain)
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(30L * 24 * 3600)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, newCookie.toString());

        Map<String,Object> body = new HashMap<>();
        body.put("accessToken", loginData.get("accessToken"));
        body.put("user", loginData.get("user"));

        return ResponseEntity.ok().body(Result.success(body));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        Cookie cookie = WebUtils.getCookie(request, "refresh_token");
        if (cookie != null) {
            refreshTokenService.revokeByPlain(cookie.getValue()); // 标记 DB 中的 token 为 revoked
        }
        // 清除 cookie（与设置 cookie 时保持相同 path/domain）
        ResponseCookie expired = ResponseCookie.from("refresh_token", "")
                .httpOnly(true).secure(false).path("/").maxAge(0).sameSite("Lax").build();
        response.addHeader(HttpHeaders.SET_COOKIE, expired.toString());
        return ResponseEntity.ok().body(Result.success());
    }
}