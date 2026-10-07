package com.fz.scitech.platform.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具(决策#35-①:token 加 uid claim——结构变更不可上线后做)。
 * claims: sub=用户名, uid=用户主键(字符串), role=角色(签发时快照,鉴权以库中最新为准)
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expireMillis;

    public JwtUtil(@Value("${sc.jwt.secret}") String secret,
                   @Value("${sc.jwt.expire-hours}") long expireHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMillis = expireHours * 3600_000;
    }

    public String create(Long userId, String username, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .claim("uid", String.valueOf(userId))
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                .signWith(key)
                .compact();
    }

    /** 解析失败(过期/伪造)抛 JwtException,由调用方统一处理 */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
    }
}
