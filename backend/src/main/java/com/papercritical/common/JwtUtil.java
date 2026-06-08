package com.papercritical.common;

import io.jsonwebtoken.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    public String generateToken(Long teacherId, String username) {
        return Jwts.builder()
                .setSubject(String.valueOf(teacherId))
                .claim("username", username)
                .claim("role", "TEACHER")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(SignatureAlgorithm.HS256, secret)
                .compact();
    }

    public String generateStudentToken(Long studentId, String name) {
        return Jwts.builder()
                .setSubject(String.valueOf(studentId))
                .claim("username", name)
                .claim("role", "STUDENT")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(SignatureAlgorithm.HS256, secret)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .setSigningKey(secret)
                .parseClaimsJws(token)
                .getBody();
    }

    public Long getTeacherId(String token) {
        return Long.valueOf(parseToken(token).getSubject());
    }

    public Long getSubjectId(String token) {
        return Long.valueOf(parseToken(token).getSubject());
    }

    public String getRole(String token) {
        Object role = parseToken(token).get("role");
        return role == null ? "TEACHER" : String.valueOf(role);
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }
}
