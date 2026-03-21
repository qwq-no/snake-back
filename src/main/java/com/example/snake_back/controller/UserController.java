package com.example.snake_back.controller;

import com.example.snake_back.common.result.Result;
import com.example.snake_back.pojo.dto.UserLoginDto;
import com.example.snake_back.pojo.dto.UserRegisterDto;
import com.example.snake_back.service.UserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody UserLoginDto userLoginDto) {
        try {
            String tokenPlain = userService.login(userLoginDto, "web", "127.0.0.1");

            ResponseCookie cookie = ResponseCookie.from("refresh_token", tokenPlain)
                    .httpOnly(true)
                    .secure(true)
                    .path("/")
                    .maxAge(30L * 24 * 3600)
                    .sameSite("Lax")
                    .build();

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(Result.success());
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