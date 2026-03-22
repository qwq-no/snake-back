package com.example.snake_back.common.filter;

import com.example.snake_back.common.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    // 放行路径（按你的实际接口改）
    private static final List<String> WHITE_LIST = List.of(
            "/api/user/login",
            "/api/user/register",
            "/api/refresh/login",
            "/error"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // OPTIONS 预检请求放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        return WHITE_LIST.stream().anyMatch(p -> pathMatcher.match(p, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")) {
            write401(response, "Missing or invalid Authorization header");
            return;
        }

        String token = auth.substring(7).trim();
        if (token.isEmpty()) {
            write401(response, "Empty token");
            return;
        }

        try {
            Claims claims = jwtUtil.parseToken(token);

            // 你当前token里放的是 "UserId"
            String userId = claims.get("UserId", String.class);
            if (userId == null ||userId.isBlank()) {
                write401(response, "UserId missing in token");
                return;
            }

            // 放到请求上下文，Controller里可直接取
            request.setAttribute("currentUserId", userId);
            // 如有需要也可放 username
            // request.setAttribute("currentUsername", claims.get("username", String.class));

            filterChain.doFilter(request, response);

        } catch (ExpiredJwtException e) {
            write401(response, "Token expired");
        } catch (JwtException | IllegalArgumentException e) {
            write401(response, "Token invalid");
        }
    }

    private void write401(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":401,\"msg\":\"" + msg + "\"}");
    }
}