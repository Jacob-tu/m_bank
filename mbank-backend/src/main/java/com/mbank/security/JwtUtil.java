package com.mbank.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

/**
 * JWT 工具类
 * 负责 Token 的生成、解析和校验
 */
@Component
public class JwtUtil {

    private final SecretKey secretKey;
    private final long expirationHours;

    public JwtUtil(@Value("${mbank.jwt.secret}") String secret,
                   @Value("${mbank.jwt.expiration-hours:24}") long expirationHours) {
        byte[] keyBytes = Base64.getDecoder().decode(secret);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationHours = expirationHours;
    }

    /**
     * 生成 JWT Token
     *
     * @param userId 用户ID（作为 subject）
     * @param phone  手机号（自定义 claim）
     * @return JWT Token 字符串
     */
    public String generateToken(Long userId, String phone) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationHours * 3600 * 1000);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("phone", phone)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析 Token，返回 Claims
     *
     * @param token JWT Token 字符串
     * @return Claims
     * @throws JwtException Token 无效或过期
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从 Token 中提取用户ID
     *
     * @param token JWT Token 字符串
     * @return 用户ID
     */
    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        return Long.parseLong(claims.getSubject());
    }

    /**
     * 获取 Token 的签发时间
     *
     * @param token JWT Token 字符串
     * @return 签发时间（毫秒时间戳）
     */
    public Date getIssuedAt(String token) {
        return parseToken(token).getIssuedAt();
    }
}
