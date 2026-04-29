package com.insideout.backend.global.security.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

	private final Key key;
	private final long accessTokenExpirationMs;
	private final long refreshTokenExpirationMs;

	public JwtTokenProvider(
		@Value("${security.jwt.secret}") String secret,
		@Value("${security.jwt.access-token-expiration-ms}") long accessTokenExpirationMs,
		@Value("${security.jwt.refresh-token-expiration-ms}") long refreshTokenExpirationMs
	) {
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.accessTokenExpirationMs = accessTokenExpirationMs;
		this.refreshTokenExpirationMs = refreshTokenExpirationMs;
	}

	public String generateAccessToken(String subject) {
		return generateToken(subject, accessTokenExpirationMs);
	}

	public String generateRefreshToken(String subject) {
		return generateToken(subject, refreshTokenExpirationMs);
	}

	private String generateToken(String subject, long expirationMs) {
		Date now = new Date();
		Date expiry = new Date(now.getTime() + expirationMs);

		return Jwts.builder()
			.setSubject(subject)
			.setIssuedAt(now)
			.setExpiration(expiry)
			.signWith(key, SignatureAlgorithm.HS256)
			.compact();
	}
}
