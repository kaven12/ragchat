package com.example.ragchat.security;

import com.example.ragchat.dto.response.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${rate-limit.requests-per-minute:60}")
    private int requestsPerMinute;

    @Value("${rate-limit.block-duration-minutes:15}")
    private int blockDurationMinutes;

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        String clientIp = getClientIp(request);
        
        String blockKey = "rate:limit:block:" + clientIp;
        String countKey = "rate:limit:count:" + clientIp;
        
        // 检查是否被封禁
        if (Boolean.TRUE.equals(redisTemplate.hasKey(blockKey))) {
            response.setStatus(HttpServletResponse.SC_TOO_MANY_REQUESTS);
            response.setContentType("application/json");
            response.getWriter().write(objectMapper.writeValueAsString(
                    ErrorResponse.of("RATE_LIMIT_EXCEEDED", "请求过于频繁，请稍后重试")
            ));
            return;
        }
        
        // 获取当前请求计数
        String countStr = redisTemplate.opsForValue().get(countKey);
        int count = countStr != null ? Integer.parseInt(countStr) : 0;
        
        if (count >= requestsPerMinute) {
            // 超过限制，封禁IP
            redisTemplate.opsForValue().set(blockKey, "true", Duration.ofMinutes(blockDurationMinutes));
            redisTemplate.delete(countKey);
            
            response.setStatus(HttpServletResponse.SC_TOO_MANY_REQUESTS);
            response.setContentType("application/json");
            response.getWriter().write(objectMapper.writeValueAsString(
                    ErrorResponse.of("RATE_LIMIT_EXCEEDED", "请求过于频繁，请稍后重试")
            ));
            return;
        }
        
        // 增加计数
        redisTemplate.opsForValue().increment(countKey);
        // 设置过期时间为1分钟
        if (count == 0) {
            redisTemplate.expire(countKey, Duration.ofMinutes(1));
        }
        
        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 如果是多个代理，取第一个IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
