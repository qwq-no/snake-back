package com.example.snake_back.websocket;

import com.example.snake_back.manager.SessionContextManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

import java.util.Map;

/**
 * WebSocket 连接数限流 — 在握手阶段拒绝超额连接，返回 503。
 * 两层防护：
 * 1. 总连接数上限（默认 1000）
 * 2. 单 IP 连接数上限（默认 50）
 * 单 IP 计数是近似的：session 关闭时通过
 * {@link CoreHandler#afterConnectionClosed} 调用 {@link #decrementIp} 清理。
 */
@Component
public class ConnectionLimitInterceptor implements HandshakeInterceptor {

    private static final int MAX_TOTAL_CONNECTIONS = 1000;
    private static final int MAX_PER_IP = 50;

    private final SessionContextManager sessionContextManager;
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger> ipCounts =
            new java.util.concurrent.ConcurrentHashMap<>();

    public ConnectionLimitInterceptor(SessionContextManager sessionContextManager) {
        this.sessionContextManager = sessionContextManager;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        // 1. 总连接数检查
        int current = sessionContextManager.getSessionContextMap().size();
        if (current >= MAX_TOTAL_CONNECTIONS) {
            response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            return false;
        }

        // 2. 单 IP 连接数检查
        String ip = getClientIp(request);
        if (ip != null) {
            java.util.concurrent.atomic.AtomicInteger count = ipCounts.computeIfAbsent(
                    ip, k -> new java.util.concurrent.atomic.AtomicInteger(0));
            if (count.incrementAndGet() > MAX_PER_IP) {
                count.decrementAndGet(); // 回退
                response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
                return false;
            }
            // 将 IP 存入 attributes，供 afterHandshake 或 close 时清理
            attributes.put("clientIp", ip);
        }

        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 握手失败时清理 IP 计数
        if (exception != null) {
            String ip = getClientIp(request);
            if (ip != null) {
                decrementIp(ip);
            }
        }
    }

    /**
     * session 关闭时由 CoreHandler 调用，清理该 IP 的计数。
     */
    public void decrementIp(String ip) {
        if (ip == null) return;
        java.util.concurrent.atomic.AtomicInteger count = ipCounts.get(ip);
        if (count != null) {
            int v = count.decrementAndGet();
            if (v <= 0) {
                ipCounts.remove(ip);
            }
        }
    }

    private String getClientIp(ServerHttpRequest request) {
        try {
            // Spring 的 ServerHttpRequest 包装了 ServletServerHttpRequest
            if (request instanceof org.springframework.http.server.ServletServerHttpRequest servletRequest) {
                jakarta.servlet.http.HttpServletRequest req = servletRequest.getServletRequest();
                String xff = req.getHeader("X-Forwarded-For");
                if (xff != null && !xff.isBlank()) {
                    return xff.split(",")[0].trim();
                }
                return req.getRemoteAddr();
            }
        } catch (Exception ignored) {
        }
        // fallback
        var remote = request.getRemoteAddress();
        return remote != null ? remote.getAddress().getHostAddress() : null;
    }
}
