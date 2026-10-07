package com.fz.scitech.platform.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * JwtUtil 单元测试(决策#35-①:token 必须带 uid claim)
 */
class JwtUtilTest {

    private static final String SECRET = "test-secret-key-256bit-minimum-length!!";

    @Test
    void uidClaimRoundTrip() {
        JwtUtil u = new JwtUtil(SECRET, 1);
        String t = u.create(7L, "zhangsan", "teacher");
        Claims c = u.parse(t);
        assertEquals(7L, Long.parseLong(c.get("uid", String.class)));
        assertEquals("zhangsan", c.getSubject());
        assertEquals("teacher", c.get("role", String.class));
    }

    @Test
    void expiredTokenThrows() {
        // 负过期时长:签发即在过期时刻之前,parse 必抛 ExpiredJwtException
        JwtUtil u = new JwtUtil(SECRET, -1);
        String t = u.create(7L, "zhangsan", "teacher");
        assertThrows(ExpiredJwtException.class, () -> u.parse(t));
    }

    @Test
    void forgedTokenThrows() {
        JwtUtil a = new JwtUtil(SECRET, 1);
        JwtUtil b = new JwtUtil("another-secret-key-256bit-minimum-length", 1);
        String t = a.create(7L, "zhangsan", "teacher");
        assertThrows(Exception.class, () -> b.parse(t));
    }
}
