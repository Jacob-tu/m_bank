package com.mbank.security;

import com.mbank.entity.User;
import com.mbank.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;

/**
 * JWT 认证过滤器
 * 每次请求执行一次，验证 Bearer Token 并设置 SecurityContext
 *
 * MF-01 修复：在验证 Token 签名和有效期后，额外检查：
 * 1. passwordChangedAt：Token 签发时间早于密码修改时间则拒绝
 * 2. 用户状态：LOCKED_TEMP / FROZEN 状态直接返回 401
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        // 未携带 Token，直接放行（由 Security 配置决定是否拒绝）
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            Claims claims = jwtUtil.parseToken(token);
            Long userId = Long.parseLong(claims.getSubject());

            // 从数据库加载用户（MF-01：检查最新状态）
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                sendUnauthorized(response, "用户不存在");
                return;
            }

            // MF-01 修复：检查用户状态，LOCKED_TEMP / FROZEN 拒绝认证
            if ("FROZEN".equals(user.getStatus()) || "LOCKED_TEMP".equals(user.getStatus())) {
                sendUnauthorized(response, "请重新登录");
                return;
            }

            // MF-01 修复：检查密码修改时间，Token 签发早于密码修改则失效
            if (user.getPasswordChangedAt() != null) {
                LocalDateTime tokenIssuedAt = claims.getIssuedAt()
                        .toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                if (tokenIssuedAt.isBefore(user.getPasswordChangedAt())) {
                    sendUnauthorized(response, "请重新登录");
                    return;
                }
            }

            // 认证通过：将 userId 设入 SecurityContext
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (JwtException e) {
            log.warn("JWT 验证失败: {}", e.getMessage());
            sendUnauthorized(response, "请重新登录");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 返回 401 未认证响应
     */
    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                String.format("{\"code\":401,\"message\":\"%s\",\"data\":null}", message)
        );
    }
}
