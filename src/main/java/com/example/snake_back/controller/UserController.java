package com.example.snake_back.controller;

import com.example.snake_back.common.result.Result;
import com.example.snake_back.pojo.dto.UserLoginDto;
import com.example.snake_back.pojo.dto.UserRegisterDto;
import com.example.snake_back.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 注册接口（最小示例，接收无校验注解的 DTO）
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/getId")
    public Result<String> getId(HttpServletRequest request) {
        Object uid = request.getAttribute("currentUserId");
        if (uid == null) {
            return Result.error("unauthorized");
        }
        return Result.success(uid.toString());
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody UserLoginDto userLoginDto) {
        try {
            Map<String, Object> loginData = userService.login(userLoginDto, "web", "127.0.0.1");
            String refreshToken = loginData.get("refreshToken").toString();
            ResponseCookie cookie = ResponseCookie.from("refresh_token", refreshToken )
                    .httpOnly(true)
                    .secure(false)
                    .path("/")
                    .maxAge(30L * 24 * 3600)
                    .sameSite("Lax")
                    .build();
            Map<String, Object> body = new HashMap<>();
            body.put("accessToken", loginData.get("accessToken"));
            body.put("id", loginData.get("id"));
            body.put("username", loginData.get("username"));
            body.put("displayName", loginData.get("displayName"));
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(Result.success(body));
        } catch (Exception e) {
            return ResponseEntity.ok().body(Result.error(e.getMessage()));
        }
    }
    @PostMapping("/register")
    public Result<String> register(@RequestBody UserRegisterDto dto) {
        try {
            userService.register(dto);
            return Result.success();
        }catch (Exception e){
            return Result.error(e.getMessage());
        }
    }
}