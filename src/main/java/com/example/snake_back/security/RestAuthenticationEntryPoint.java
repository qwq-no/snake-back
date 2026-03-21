package com.example.snake_back.security;

import com.example.snake_back.common.result.Result;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 自包含的 AuthenticationEntryPoint，不依赖 Jackson，返回 {"code":0,"msg":"..."}
 */
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");

        String msg = authException == null ? "Unauthorized" : authException.getMessage();
        String json = buildSimpleResultJson(0, msg);

        response.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
    }

    // 简单且安全的 JSON 转义（处理引号、反斜杠及控制字符）
    private String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '\"': sb.append("\\\""); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c <= 0x1F) {
                        sb.append(String.format("\\u%04x", (int)c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    // 直接构建与你项目 Result<T> 兼容的简单 JSON: {"code":0,"msg":"..."}
    private String buildSimpleResultJson(int code, String msg) {
        return "{\"code\":" + code + ",\"msg\":\"" + escapeJson(msg) + "\"}";
    }
}